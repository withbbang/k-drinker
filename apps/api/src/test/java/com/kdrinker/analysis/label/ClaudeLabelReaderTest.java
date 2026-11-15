package com.kdrinker.analysis.label;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.ObjectMappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.kdrinker.analysis.claude.ClaudeCalls;
import com.kdrinker.analysis.claude.ClaudeProperties;
import com.kdrinker.media.FocusBox;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeLabelReaderTest {

	private final ClaudeLabelReader reader = new ClaudeLabelReader(
			new ClaudeCalls(new ClaudeProperties("k", "claude-opus-5-5", Duration.ofSeconds(1), 0), null));

	private JsonNode body(MessageCreateParams params) throws Exception {
		return ObjectMappers.jsonMapper().readTree(ObjectMappers.jsonMapper().writeValueAsString(params._body()));
	}

	@Test
	void 이미지_블록이_텍스트보다_앞서고_메모는_사용자_메시지에만_들어간다() throws Exception {
		// given
		List<byte[]> images = List.of(new byte[] {1}, new byte[] {2});

		// when
		JsonNode body = body(reader.buildRequest(images, "별점 4.5, 쌉쌀함").build());

		// then
		JsonNode content = body.get("messages").get(0).get("content");
		assertThat(content).hasSize(3);
		assertThat(content.get(0).get("type").asText()).isEqualTo("image");
		assertThat(content.get(1).get("type").asText()).isEqualTo("image");
		assertThat(content.get(2).get("type").asText()).isEqualTo("text");
		assertThat(content.get(2).get("text").asText()).contains("<memo>").contains("별점 4.5, 쌉쌀함");
		assertThat(body.get("system").toString()).doesNotContain("별점 4.5, 쌉쌀함");
		assertThat(body.get("system").toString()).contains(LabelPrompt.SYSTEM.substring(0, 10));
	}

	@Test
	void 메모가_없으면_메모_없음으로_표시한다() throws Exception {
		JsonNode body = body(reader.buildRequest(List.of(new byte[] {1}), null).build());

		assertThat(body.get("messages").get(0).get("content").get(1).get("text").asText()).contains("메모 없음");
	}

	@Test
	void 응답을_LabelResult로_바꾼다() {
		// given
		var reply = new ClaudeLabelReader.Reply("술", "탁주", 6.0, 750, "양조장", 4.5, "달콤", "전",
				List.of(new ClaudeLabelReader.Box(0, 0.1, 0.1, 0.5, 0.8)));

		// when
		LabelResult result = ClaudeLabelReader.toResult(reply);

		// then
		assertThat(result.abv()).isEqualByComparingTo("6.0");
		assertThat(result.rating()).isEqualByComparingTo("4.5");
		assertThat(result.boxes()).containsExactly(new PhotoBox(0, new FocusBox(0.1, 0.1, 0.5, 0.8)));
	}
}
