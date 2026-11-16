package com.kdrinker.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kdrinker.telegram.dto.CallbackQuery;
import com.kdrinker.telegram.dto.Document;
import com.kdrinker.telegram.dto.InlineButton;
import com.kdrinker.telegram.dto.PhotoSize;
import com.kdrinker.telegram.dto.TelegramMessage;
import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.dto.TelegramUpdate;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 텔레그램 Bot API 클라이언트. 실패는 토큰이 없는 {@link TelegramApiException}으로만 알린다.
 * 읽기 타임아웃(spring.http.clients.read-timeout)은 롱 폴링 대기 시간보다 길어야 한다.
 */
@Component
@EnableConfigurationProperties(TelegramProperties.class)
public class TelegramClient {

	private static final int MAX_ALBUM = 10;

	private final RestClient restClient;
	private final String baseUrl;
	private final String botToken;
	private final String chatId;

	public TelegramClient(RestClient.Builder builder, TelegramProperties properties) {
		this.restClient = builder.build();
		this.baseUrl = properties.baseUrl();
		this.botToken = blankIfNull(properties.botToken());
		this.chatId = blankIfNull(properties.chatId());
	}

	public boolean isConfigured() {
		return !botToken.isBlank() && !chatId.isBlank();
	}

	public boolean isOwnerChat(long chatId) {
		return isConfigured() && this.chatId.equals(Long.toString(chatId));
	}

	/** 보낸 메시지의 message_id. */
	public long sendMessage(TelegramText text) {
		requireConfigured();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("chat_id", chatId);
		body.put("text", text.html());
		body.put("parse_mode", "HTML");
		if (text.keyboard() != null && !text.keyboard().isEmpty()) {
			body.put("reply_markup", Map.of("inline_keyboard", keyboardOf(text.keyboard())));
		}
		SentMessage sent = result(postJson("sendMessage", body, new ParameterizedTypeReference<Envelope<SentMessage>>() {
		}));
		if (sent == null) {
			throw new TelegramApiException("텔레그램 응답에 message_id가 없습니다.");
		}
		return sent.messageId();
	}

	public void removeKeyboard(long messageId) {
		requireConfigured();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("chat_id", chatId);
		body.put("message_id", messageId);
		body.put("reply_markup", Map.of("inline_keyboard", List.of()));
		postJson("editMessageReplyMarkup", body, new ParameterizedTypeReference<Envelope<Object>>() {
		});
	}

	public void answerCallback(String callbackQueryId, String textOrNull) {
		requireConfigured();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("callback_query_id", callbackQueryId);
		if (textOrNull != null) {
			body.put("text", textOrNull);
		}
		postJson("answerCallbackQuery", body, new ParameterizedTypeReference<Envelope<Object>>() {
		});
	}

	/** 1장이면 sendPhoto, 2~10장이면 sendMediaGroup. 파일을 multipart로 올린다. */
	public void sendPhotos(List<Path> jpegs) {
		requireConfigured();
		if (jpegs == null || jpegs.isEmpty() || jpegs.size() > MAX_ALBUM) {
			throw new TelegramApiException("사진은 1장에서 10장까지 보낼 수 있습니다.");
		}
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("chat_id", chatId);
		if (jpegs.size() == 1) {
			form.add("photo", new FileSystemResource(jpegs.get(0)));
			postMultipart("sendPhoto", form);
			return;
		}
		// attach 이름은 우리가 정한 값이라 JSON을 직접 이어 붙여도 이스케이프할 것이 없다.
		List<String> media = new ArrayList<>();
		for (int i = 0; i < jpegs.size(); i++) {
			form.add("photo" + i, new FileSystemResource(jpegs.get(i)));
			media.add("{\"type\":\"photo\",\"media\":\"attach://photo" + i + "\"}");
		}
		form.add("media", "[" + String.join(",", media) + "]");
		postMultipart("sendMediaGroup", form);
	}

	/** {@code offset}보다 작은 업데이트는 확인 처리된다. timeoutSeconds는 읽기 타임아웃보다 짧아야 한다. */
	public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
		requireConfigured();
		Map<String, Object> body = Map.of("offset", offset, "timeout", timeoutSeconds, "allowed_updates",
				List.of("message", "callback_query"));
		List<RawUpdate> updates = result(
				postJson("getUpdates", body, new ParameterizedTypeReference<Envelope<List<RawUpdate>>>() {
				}));
		if (updates == null) {
			return List.of();
		}
		return updates.stream().map(RawUpdate::toUpdate).toList();
	}

	/** getFile로 경로를 알아낸 뒤 파일 바이트를 내려받는다. */
	public byte[] downloadFile(String fileId) {
		requireConfigured();
		RawFile file = result(postJson("getFile", Map.of("file_id", fileId),
				new ParameterizedTypeReference<Envelope<RawFile>>() {
				}));
		if (file == null || file.filePath() == null) {
			throw new TelegramApiException("텔레그램 응답에 file_path가 없습니다.");
		}
		URI uri = UriComponentsBuilder.fromUriString(baseUrl)
			.pathSegment("file", "bot" + botToken)
			.path("/" + file.filePath())
			.build()
			.toUri();
		byte[] bytes = call(() -> restClient.get().uri(uri).retrieve().body(byte[].class));
		if (bytes == null) {
			throw new TelegramApiException("텔레그램 파일 응답이 비어 있습니다.");
		}
		return bytes;
	}

	private void requireConfigured() {
		if (!isConfigured()) {
			throw new TelegramApiException("텔레그램 설정이 비어 있습니다.");
		}
	}

	private static List<List<Map<String, String>>> keyboardOf(List<List<InlineButton>> keyboard) {
		return keyboard.stream()
			.map(row -> row.stream().map(b -> Map.of("text", b.text(), "callback_data", b.callbackData())).toList())
			.toList();
	}

	private <T> Envelope<T> postJson(String method, Object body, ParameterizedTypeReference<Envelope<T>> type) {
		return call(() -> restClient.post()
			.uri(methodUri(method))
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(type));
	}

	private void postMultipart(String method, MultiValueMap<String, Object> form) {
		Envelope<Object> envelope = call(() -> restClient.post()
			.uri(methodUri(method))
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(form)
			.retrieve()
			.body(new ParameterizedTypeReference<Envelope<Object>>() {
			}));
		result(envelope);
	}

	/** ok:false면 설명만 담아 던지고 result를 꺼낸다. */
	private static <T> T result(Envelope<T> envelope) {
		if (envelope == null || !envelope.ok()) {
			String description = envelope == null ? "빈 응답" : envelope.description();
			throw new TelegramApiException("텔레그램 응답 오류: " + description);
		}
		return envelope.result();
	}

	private <T> T call(Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientResponseException e) {
			throw new TelegramApiException("텔레그램 응답 오류: HTTP " + e.getStatusCode().value());
		} catch (RuntimeException e) {
			// 연결 실패나 잘못된 URL의 원본 메시지에는 토큰이 포함되므로 종류만 남긴다.
			throw new TelegramApiException("텔레그램 요청 실패: " + e.getClass().getSimpleName());
		}
	}

	private URI methodUri(String method) {
		return UriComponentsBuilder.fromUriString(baseUrl).pathSegment("bot" + botToken, method).build().toUri();
	}

	private static String blankIfNull(String value) {
		return value == null ? "" : value;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Envelope<T>(boolean ok, T result, String description) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SentMessage(@JsonProperty("message_id") long messageId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawFile(@JsonProperty("file_path") String filePath) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawUpdate(@JsonProperty("update_id") long updateId, RawMessage message,
			@JsonProperty("callback_query") RawCallback callbackQuery) {

		TelegramUpdate toUpdate() {
			return new TelegramUpdate(updateId, message == null ? null : message.toMessage(),
					callbackQuery == null ? null : callbackQuery.toCallback());
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawChat(long id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawPhoto(@JsonProperty("file_id") String fileId, int width, int height,
			@JsonProperty("file_size") Long fileSize) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawDocument(@JsonProperty("file_id") String fileId, @JsonProperty("mime_type") String mimeType,
			@JsonProperty("file_size") Long fileSize) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawMessage(@JsonProperty("message_id") long messageId, RawChat chat, String text, String caption,
			@JsonProperty("media_group_id") String mediaGroupId, List<RawPhoto> photo, RawDocument document) {

		TelegramMessage toMessage() {
			List<PhotoSize> photos = photo == null ? List.of()
					: photo.stream().map(p -> new PhotoSize(p.fileId(), p.width(), p.height(), p.fileSize())).toList();
			Document doc = document == null ? null
					: new Document(document.fileId(), document.mimeType(), document.fileSize());
			return new TelegramMessage(messageId, chat == null ? 0 : chat.id(), text, caption, mediaGroupId, photos,
					doc);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawFrom(long id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawCallback(String id, RawFrom from, RawMessage message, String data) {

		CallbackQuery toCallback() {
			long chat = message != null && message.chat() != null ? message.chat().id() : from == null ? 0 : from.id();
			return new CallbackQuery(id, chat, message == null ? null : message.messageId(), data);
		}
	}

}
