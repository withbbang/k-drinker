package com.kdrinker.analysis.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import java.util.Base64;
import java.util.Optional;

/** Claude 베타 Messages 호출의 공통부. 분석 단계(라벨·조사·캡션)가 base()에 내용을 더해 structured()로 부른다. */
public class ClaudeCalls {

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
		if (client == null) {
			throw new ClaudeUnavailableException("ANTHROPIC_API_KEY가 설정되지 않았다");
		}
		try {
			StructuredMessage<T> message = client.beta().messages().create(toStructured(builder, type));
			Optional<BetaStopReason> stop = message.stopReason();
			if (stop.isPresent() && stop.get().equals(BetaStopReason.REFUSAL)) {
				throw new ClaudeUnavailableException("Claude가 요청을 거절했다");
			}
			if (stop.isPresent() && stop.get().equals(BetaStopReason.MAX_TOKENS)) {
				throw new ClaudeUnavailableException("Claude 응답이 max_tokens에서 잘렸다");
			}
			return message.content().stream()
					.flatMap(block -> block.text().stream())
					.map(text -> text.text())
					.findFirst()
					.orElseThrow(() -> new ClaudeUnavailableException("Claude 응답에 텍스트가 없다"));
		} catch (ClaudeUnavailableException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new ClaudeUnavailableException("Claude 호출 실패: " + e.getMessage(), e);
		}
	}

	static <T> StructuredMessageCreateParams<T> toStructured(MessageCreateParams.Builder builder, Class<T> type) {
		return builder.outputConfig(type).build();
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
