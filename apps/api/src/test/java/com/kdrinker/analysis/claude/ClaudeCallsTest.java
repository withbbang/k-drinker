package com.kdrinker.analysis.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.core.ObjectMappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import java.time.Duration;
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
}
