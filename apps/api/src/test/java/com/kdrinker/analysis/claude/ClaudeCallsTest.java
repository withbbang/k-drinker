package com.kdrinker.analysis.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.core.ObjectMappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import java.time.Duration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.StructuredContentBlock;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredTextBlock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ClaudeCallsTest {

	record Answer(String value) {}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(Cfg.class);

	@Configuration
	@Import(ClaudeConfig.class)
	static class Cfg {}

	private ClaudeCalls calls(String apiKey) {
		return new ClaudeCalls(new ClaudeProperties(apiKey, "claude-opus-5-5", Duration.ofSeconds(120), 2), null);
	}

	private static String json(MessageCreateParams params) {
		try {
			return ObjectMappers.jsonMapper().writeValueAsString(params._body());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void 기본_요청은_모델_노력도_폴백_베타헤더를_담는다() {
		// given
		MessageCreateParams params = calls("k").base().addUserMessage("x").build();

		// when
		String body = json(params);

		// then
		assertThat(body).contains("claude-opus-5-5");
		assertThat(body).contains("16000");
		assertThat(body).contains("medium");
		assertThat(body).contains("\"fallbacks\":\"default\"");
		assertThat(params._headers().values("anthropic-beta")).contains("server-side-fallback-2026-07-01");
	}

	@Test
	void 구조화_요청으로_바꿔도_노력도와_폴백이_유지된다() {
		// given
		MessageCreateParams.Builder builder = calls("k").base().addUserMessage("x");

		// when
		StructuredMessageCreateParams<Answer> params = ClaudeCalls.toStructured(builder, Answer.class);

		// then
		assertThat(params.outputType()).isEqualTo(Answer.class);
		assertThat(json(params.rawParams())).contains("medium").contains("json_schema");
		assertThat(params.fallbacks()).isPresent();
	}

	@Test
	void 키가_비어도_컨텍스트는_뜨고_호출_시점에_실패한다() {
		runner.withPropertyValues("app.anthropic.api-key=", "app.anthropic.model=claude-opus-5-5",
				"app.anthropic.timeout=120s", "app.anthropic.max-retries=2").run(ctx -> {
			// given
			assertThat(ctx).hasNotFailed();
			ClaudeCalls calls = ctx.getBean(ClaudeCalls.class);

			// when / then
			assertThatThrownBy(() -> calls.structured(calls.base().addUserMessage("x"), Answer.class))
					.isInstanceOf(ClaudeUnavailableException.class);
		});
	}

	@Test
	void 키가_있으면_클라이언트가_만들어진다() {
		runner.withPropertyValues("app.anthropic.api-key=test-key", "app.anthropic.model=claude-opus-5-5",
				"app.anthropic.timeout=120s", "app.anthropic.max-retries=2").run(ctx -> {
			assertThat(ctx).hasNotFailed();
			assertThat(ctx.getBean(ClaudeProperties.class).timeout()).isEqualTo(Duration.ofSeconds(120));
			assertThat(ctx.getBean(ClaudeCalls.class).hasClient()).isTrue();
		});
	}

	@Test
	void 이미지_블록은_base64_JPEG다() {
		// given / when
		String json = ClaudeCalls.imageBlock(new byte[] {1, 2, 3}).toString();

		// then
		assertThat(json).contains("image/jpeg").contains("AQID");
	}

	@SuppressWarnings("unchecked")
	private ClaudeCalls withResponse(BetaStopReason stop, Answer answer) {
		StructuredMessage<Answer> message = mock(StructuredMessage.class);
		when(message.stopReason()).thenReturn(Optional.ofNullable(stop));
		if (answer != null) {
			StructuredTextBlock<Answer> text = mock(StructuredTextBlock.class);
			when(text.text()).thenReturn(answer);
			StructuredContentBlock<Answer> block = mock(StructuredContentBlock.class);
			when(block.text()).thenReturn(Optional.of(text));
			when(message.content()).thenReturn(List.of(block));
		} else {
			when(message.content()).thenReturn(List.of());
		}
		AnthropicClient client = mock(AnthropicClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
		when(client.beta().messages().create(any(StructuredMessageCreateParams.class))).thenReturn(message);
		return new ClaudeCalls(new ClaudeProperties("k", "claude-opus-5-5", Duration.ofSeconds(1), 0), client);
	}

	@Test
	void 정상_응답이면_파싱된_값을_돌려준다() {
		ClaudeCalls calls = withResponse(BetaStopReason.END_TURN, new Answer("ok"));

		assertThat(calls.structured(calls.base().addUserMessage("x"), Answer.class)).isEqualTo(new Answer("ok"));
	}

	@Test
	void 종료_사유가_refusal이면_실패한다() {
		ClaudeCalls calls = withResponse(BetaStopReason.REFUSAL, new Answer("ok"));

		assertThatThrownBy(() -> calls.structured(calls.base().addUserMessage("x"), Answer.class))
				.isInstanceOf(ClaudeUnavailableException.class).hasMessageContaining("거절");
	}

	@Test
	void 종료_사유가_max_tokens면_실패한다() {
		ClaudeCalls calls = withResponse(BetaStopReason.MAX_TOKENS, new Answer("ok"));

		assertThatThrownBy(() -> calls.structured(calls.base().addUserMessage("x"), Answer.class))
				.isInstanceOf(ClaudeUnavailableException.class).hasMessageContaining("max_tokens");
	}

	@Test
	void 텍스트_블록이_없으면_실패한다() {
		ClaudeCalls calls = withResponse(BetaStopReason.END_TURN, null);

		assertThatThrownBy(() -> calls.structured(calls.base().addUserMessage("x"), Answer.class))
				.isInstanceOf(ClaudeUnavailableException.class);
	}

	@Test
	void 클라이언트의_임의_RuntimeException도_감싼다() {
		AnthropicClient client = mock(AnthropicClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
		when(client.beta().messages().create(any(StructuredMessageCreateParams.class)))
				.thenThrow(new IllegalArgumentException("boom"));
		ClaudeCalls calls = new ClaudeCalls(new ClaudeProperties("k", "claude-opus-5-5", Duration.ofSeconds(1), 0), client);

		assertThatThrownBy(() -> calls.structured(calls.base().addUserMessage("x"), Answer.class))
				.isInstanceOf(ClaudeUnavailableException.class).hasCauseInstanceOf(IllegalArgumentException.class);
	}

	private ClaudeCalls withRawResponse(BetaStopReason stop) {
		BetaMessage message = mock(BetaMessage.class);
		when(message.stopReason()).thenReturn(Optional.ofNullable(stop));
		AnthropicClient client = mock(AnthropicClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
		when(client.beta().messages().create(any(MessageCreateParams.class))).thenReturn(message);
		return new ClaudeCalls(new ClaudeProperties("k", "claude-opus-5-5", Duration.ofSeconds(1), 0), client);
	}

	@Test
	void 원본_호출은_pause_turn을_실패로_보지_않고_메시지를_돌려준다() {
		// given
		ClaudeCalls calls = withRawResponse(BetaStopReason.PAUSE_TURN);

		// when
		BetaMessage message = calls.create(calls.base().addUserMessage("x").build());

		// then
		assertThat(message.stopReason()).contains(BetaStopReason.PAUSE_TURN);
	}

	@Test
	void 원본_호출도_refusal이면_실패한다() {
		ClaudeCalls calls = withRawResponse(BetaStopReason.REFUSAL);

		assertThatThrownBy(() -> calls.create(calls.base().addUserMessage("x").build()))
				.isInstanceOf(ClaudeUnavailableException.class).hasMessageContaining("거절");
	}

	@Test
	void 원본_호출도_max_tokens면_실패한다() {
		ClaudeCalls calls = withRawResponse(BetaStopReason.MAX_TOKENS);

		assertThatThrownBy(() -> calls.create(calls.base().addUserMessage("x").build()))
				.isInstanceOf(ClaudeUnavailableException.class).hasMessageContaining("max_tokens");
	}

	@Test
	void 원본_호출은_키가_없으면_실패한다() {
		ClaudeCalls calls = calls("");

		assertThatThrownBy(() -> calls.create(calls.base().addUserMessage("x").build()))
				.isInstanceOf(ClaudeUnavailableException.class);
	}

	@Test
	void 원본_호출의_임의_RuntimeException도_감싼다() {
		AnthropicClient client = mock(AnthropicClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
		when(client.beta().messages().create(any(MessageCreateParams.class)))
				.thenThrow(new IllegalStateException("boom"));
		ClaudeCalls calls = new ClaudeCalls(new ClaudeProperties("k", "claude-opus-5-5", Duration.ofSeconds(1), 0), client);

		assertThatThrownBy(() -> calls.create(calls.base().addUserMessage("x").build()))
				.isInstanceOf(ClaudeUnavailableException.class).hasCauseInstanceOf(IllegalStateException.class);
	}
}
