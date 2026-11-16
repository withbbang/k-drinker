package com.kdrinker.analysis.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Claude 베타 Messages 호출의 공통부. 분석 단계(라벨·조사·캡션)가 base()에 내용을 더해 structured()로 부른다. */
public class ClaudeCalls implements AutoCloseable {

	static final long MAX_TOKENS = 16000L;

	private final ClaudeProperties props;
	private final AnthropicClient client;

	public ClaudeCalls(ClaudeProperties props, AnthropicClient client) {
		this.props = props;
		this.client = client;
	}

	boolean hasClient() {
		return client != null;
	}

	/** 모델·maxTokens·effort(medium)·서버측 폴백(베타 헤더 + fallbacks=default)을 담은 빌더. */
	public MessageCreateParams.Builder base() {
		return MessageCreateParams.builder()
				.model(props.model())
				.maxTokens(MAX_TOKENS)
				.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
				.fallbacksDefault()
				.outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build());
	}

	/** 빌더에 T의 JSON 스키마 출력을 걸어 호출하고 T로 돌려준다. 어떤 실패든 ClaudeUnavailableException. */
	public <T> T structured(MessageCreateParams.Builder builder, Class<T> type) {
		return call(() -> client.beta().messages().create(toStructured(builder, type)), StructuredMessage::stopReason,
				message -> message.content().stream()
						.flatMap(block -> block.text().stream())
						.map(text -> text.text())
						.findFirst()
						.orElseThrow(() -> new ClaudeUnavailableException("Claude 응답에 텍스트가 없다")));
	}

	/**
	 * 원본 베타 메시지를 돌려준다(서버 도구 결과 블록·pause_turn을 직접 다룰 때). structured()와 같은 실패 규칙이고,
	 * pause_turn은 실패가 아니다.
	 */
	public BetaMessage create(MessageCreateParams params) {
		return call(() -> client.beta().messages().create(params), BetaMessage::stopReason, Function.identity());
	}

	/** 키 없음·임의 RuntimeException·refusal·max_tokens를 모두 ClaudeUnavailableException으로 바꾼다. */
	private <M, R> R call(Supplier<M> send, Function<M, Optional<BetaStopReason>> stopReason, Function<M, R> extract) {
		if (client == null) {
			throw new ClaudeUnavailableException("ANTHROPIC_API_KEY가 설정되지 않았다");
		}
		try {
			M message = send.get();
			Optional<BetaStopReason> stop = stopReason.apply(message);
			if (stop.isPresent() && stop.get().equals(BetaStopReason.REFUSAL)) {
				throw new ClaudeUnavailableException("Claude가 요청을 거절했다");
			}
			if (stop.isPresent() && stop.get().equals(BetaStopReason.MAX_TOKENS)) {
				throw new ClaudeUnavailableException("Claude 응답이 max_tokens에서 잘렸다");
			}
			return extract.apply(message);
		} catch (ClaudeUnavailableException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new ClaudeUnavailableException("Claude 호출 실패: " + e.getMessage(), e);
		}
	}

	static <T> StructuredMessageCreateParams<T> toStructured(MessageCreateParams.Builder builder, Class<T> type) {
		return builder.outputConfig(type).build();
	}

	/** 종료 시 Anthropic 클라이언트(OkHttp 연결 풀·스레드)를 닫는다. 키가 없어 클라이언트가 없으면 아무것도 하지 않는다. */
	@Override
	public void close() {
		if (client != null) {
			client.close();
		}
	}

	/** base64 JPEG 이미지 content block. */
	public static BetaContentBlockParam imageBlock(byte[] jpeg) {
		return BetaContentBlockParam.ofImage(BetaImageBlockParam.builder()
				.source(BetaBase64ImageSource.builder()
						.data(Base64.getEncoder().encodeToString(jpeg))
						.mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
						.build())
				.build());
	}
}
