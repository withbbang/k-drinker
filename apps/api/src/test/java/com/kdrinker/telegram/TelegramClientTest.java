package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kdrinker.telegram.dto.InlineButton;
import com.kdrinker.telegram.dto.TelegramText;
import com.kdrinker.telegram.dto.TelegramUpdate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@RestClientTest(value = TelegramClient.class,
		properties = { "app.telegram.base-url=https://api.telegram.org", "app.telegram.bot-token=123:secret",
				"app.telegram.chat-id=42" })
class TelegramClientTest {

	private static final String TOKEN = "123:secret";
	private static final String BASE = "https://api.telegram.org/bot" + TOKEN + "/";

	@Autowired
	private TelegramClient client;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void sendMessage는_HTML과_인라인_버튼을_보내고_message_id를_돌려준다() {
		// given
		server.expect(requestTo(BASE + "sendMessage"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.chat_id").value("42"))
			.andExpect(jsonPath("$.parse_mode").value("HTML"))
			.andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].callback_data").value("ok:128:2"))
			.andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":77}}", MediaType.APPLICATION_JSON));
		TelegramText text = new TelegramText("<b>안녕</b>", List.of(List.of(new InlineButton("확인", "ok:128:2"))));

		// when
		long messageId = client.sendMessage(text);

		// then
		assertThat(messageId).isEqualTo(77);
		server.verify();
	}

	@Test
	void sendMessage_withReplyTo_sendsReplyParameters() {
		// given
		server.expect(requestTo(BASE + "sendMessage"))
			.andExpect(jsonPath("$.reply_parameters.message_id").value(77))
			.andExpect(jsonPath("$.reply_parameters.allow_sending_without_reply").value(true))
			.andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", MediaType.APPLICATION_JSON));

		// when
		client.sendMessage(TelegramText.of("안녕").withReplyTo(77L));

		// then
		server.verify();
	}

	@Test
	void 답장_대상이_없으면_reply_parameters를_보내지_않는다() {
		// given
		server.expect(requestTo(BASE + "sendMessage"))
			.andExpect(jsonPath("$.reply_parameters").doesNotExist())
			.andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", MediaType.APPLICATION_JSON));

		// when
		client.sendMessage(TelegramText.of("안녕"));

		// then
		server.verify();
	}

	@Test
	void 버튼이_없으면_reply_markup을_보내지_않는다() {
		// given
		server.expect(requestTo(BASE + "sendMessage"))
			.andExpect(jsonPath("$.reply_markup").doesNotExist())
			.andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", MediaType.APPLICATION_JSON));

		// when
		client.sendMessage(TelegramText.of("안녕"));

		// then
		server.verify();
	}

	@Test
	void 토큰이_비면_설정되지_않은_것이고_요청을_보내지_않는다() {
		// given
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer blankServer = MockRestServiceServer.bindTo(builder).build();
		TelegramClient blank = new TelegramClient(builder,
				new TelegramProperties("https://api.telegram.org", "", "42", 25, null, null, 3));

		// when, then
		assertThat(blank.isConfigured()).isFalse();
		assertThatThrownBy(() -> blank.sendMessage(TelegramText.of("안녕"))).isInstanceOf(TelegramApiException.class)
			.hasMessageContaining("설정이 비어 있습니다");
		blankServer.verify();
	}

	@Test
	void getFile이_준_경로로_파일을_내려받아_바이트를_그대로_돌려준다() {
		// given
		server.expect(requestTo(BASE + "getFile"))
			.andExpect(jsonPath("$.file_id").value("F1"))
			.andRespond(withSuccess("{\"ok\":true,\"result\":{\"file_path\":\"photos/a.jpg\"}}",
					MediaType.APPLICATION_JSON));
		byte[] bytes = { 1, 2, 3, (byte) 0xff };
		server.expect(requestTo("https://api.telegram.org/file/bot" + TOKEN + "/photos/a.jpg"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(bytes, MediaType.APPLICATION_OCTET_STREAM));

		// when
		byte[] downloaded = client.downloadFile("F1");

		// then
		assertThat(downloaded).isEqualTo(bytes);
		server.verify();
	}

	@Test
	void ok가_false면_예외다() {
		// given
		server.expect(requestTo(BASE + "sendMessage"))
			.andRespond(withSuccess("{\"ok\":false,\"description\":\"Bad Request\"}", MediaType.APPLICATION_JSON));

		// when, then
		assertThatThrownBy(() -> client.sendMessage(TelegramText.of("안녕"))).isInstanceOf(TelegramApiException.class)
			.hasMessageContaining("Bad Request");
	}

	@Test
	void HTTP_오류_예외에_토큰이_포함되지_않는다() {
		// given
		server.expect(requestTo(BASE + "sendMessage")).andRespond(withServerError());

		// when, then
		assertThatThrownBy(() -> client.sendMessage(TelegramText.of("안녕"))).isInstanceOf(TelegramApiException.class)
			.hasMessageContaining("500")
			.hasMessageNotContaining(TOKEN);
	}

	@Test
	void 설정된_채팅만_주인_채팅이다() {
		// when, then
		assertThat(client.isOwnerChat(42)).isTrue();
		assertThat(client.isOwnerChat(43)).isFalse();
	}

	@Test
	void getUpdates는_메시지와_콜백을_읽는다() {
		// given
		server.expect(requestTo(BASE + "getUpdates"))
			.andExpect(jsonPath("$.offset").value(7))
			.andExpect(jsonPath("$.timeout").value(25))
			.andExpect(jsonPath("$.allowed_updates[0]").value("message"))
			.andExpect(jsonPath("$.allowed_updates[1]").value("callback_query"))
			.andRespond(withSuccess("""
				{"ok":true,"result":[
				  {"update_id":7,"message":{"message_id":1,"chat":{"id":42},"caption":"c","media_group_id":"g",
				    "photo":[{"file_id":"p1","width":90,"height":60,"file_size":10}]}},
				  {"update_id":8,"callback_query":{"id":"cb","from":{"id":42},"message":{"message_id":5,"chat":{"id":42}},"data":"ok:1:2"}}
				]}""", MediaType.APPLICATION_JSON));

		// when
		List<TelegramUpdate> updates = client.getUpdates(7, 25);

		// then
		assertThat(updates).hasSize(2);
		assertThat(updates.get(0).message().mediaGroupId()).isEqualTo("g");
		assertThat(updates.get(0).message().photo().get(0).fileId()).isEqualTo("p1");
		assertThat(updates.get(1).callbackQuery().data()).isEqualTo("ok:1:2");
		assertThat(updates.get(1).callbackQuery().messageId()).isEqualTo(5L);
		assertThat(updates.get(1).callbackQuery().fromChatId()).isEqualTo(42);
	}

	@Test
	void 사진_한_장은_sendPhoto로_올린다(@TempDir Path dir) throws Exception {
		// given
		Path jpeg = Files.write(dir.resolve("a.jpg"), new byte[] { 1, 2 });
		server.expect(requestTo(BASE + "sendPhoto"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
			.andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));

		// when
		client.sendPhotos(List.of(jpeg));

		// then
		server.verify();
	}

	@Test
	void 사진_여러_장은_sendMediaGroup으로_올린다(@TempDir Path dir) throws Exception {
		// given
		Path a = Files.write(dir.resolve("a.jpg"), new byte[] { 1 });
		Path b = Files.write(dir.resolve("b.jpg"), new byte[] { 2 });
		server.expect(requestTo(BASE + "sendMediaGroup"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
			.andRespond(withSuccess("{\"ok\":true,\"result\":[]}", MediaType.APPLICATION_JSON));

		// when
		client.sendPhotos(List.of(a, b));

		// then
		server.verify();
	}

	@Test
	void 사진이_11장이면_예외다(@TempDir Path dir) {
		// given
		List<Path> eleven = java.util.Collections.nCopies(11, dir.resolve("a.jpg"));

		// when, then
		assertThatThrownBy(() -> client.sendPhotos(eleven)).isInstanceOf(TelegramApiException.class);
	}

}
