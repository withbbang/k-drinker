package com.kdrinker.analysis.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaWebSearchResultBlock;
import com.anthropic.models.beta.messages.BetaWebSearchToolResultBlock;
import com.anthropic.models.beta.messages.BetaWebSearchToolResultBlockContent;
import com.anthropic.models.beta.messages.BetaWebSearchToolResultError;
import com.anthropic.models.beta.messages.BetaWebSearchToolResultErrorCode;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.kdrinker.analysis.claude.ClaudeCalls;
import com.kdrinker.analysis.claude.ClaudeProperties;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClaudeBreweryResearcherTest {

	private final ClaudeBreweryResearcher researcher = new ClaudeBreweryResearcher(
			new ClaudeCalls(new ClaudeProperties("k", "claude-opus-5-5", Duration.ofSeconds(1), 0), null));

	private static ResearchedBrewery candidate(String sourceUrl) {
		return new ResearchedBrewery("새술양조장", null, null, null, null, sourceUrl);
	}

	private static String json(MessageCreateParams params) {
		try {
			return ObjectMappers.jsonMapper().writeValueAsString(params._body());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static BetaContentBlock results(String... urls) {
		List<BetaWebSearchResultBlock> blocks = java.util.Arrays.stream(urls)
				.map(u -> BetaWebSearchResultBlock.builder().url(u).title("t").encryptedContent("e")
						.pageAge((String) null).build())
				.toList();
		return BetaContentBlock.ofWebSearchToolResult(BetaWebSearchToolResultBlock.builder()
				.toolUseId("srvtoolu_1")
				.content(BetaWebSearchToolResultBlockContent.ofResultBlocks(blocks))
				.build());
	}

	@Test
	void 출처_URL이_비어_있으면_버린다() {
		assertThat(ClaudeBreweryResearcher.accept(candidate(null), Set.of("https://a.com"))).isEmpty();
		assertThat(ClaudeBreweryResearcher.accept(candidate("  "), Set.of("https://a.com"))).isEmpty();
	}

	@Test
	void 검색_결과에_없는_URL이면_버린다() {
		assertThat(ClaudeBreweryResearcher.accept(candidate("https://made-up.com/x"), Set.of("https://a.com/x")))
				.isEmpty();
	}

	@Test
	void 검색_결과에_있는_URL이면_유지한다() {
		ResearchedBrewery c = candidate("https://a.com/x");

		assertThat(ClaudeBreweryResearcher.accept(c, Set.of("https://a.com/x"))).contains(c);
	}

	@Test
	void 끝_슬래시와_앞뒤_공백_차이는_같은_URL로_본다() {
		ResearchedBrewery c = candidate(" https://a.com/x/ ");

		assertThat(ClaudeBreweryResearcher.accept(c, Set.of("https://a.com/x"))).contains(c);
		assertThat(ClaudeBreweryResearcher.accept(candidate("https://a.com/x"), Set.of("https://a.com/x/")))
				.isPresent();
	}

	@Test
	void 호스트_대소문자_차이는_같은_URL로_본다() {
		assertThat(ClaudeBreweryResearcher.accept(candidate("https://A.com/x"), Set.of("https://a.com/x")))
				.isPresent();
	}

	@Test
	void 경로_대소문자가_다르면_다른_URL이다() {
		assertThat(ClaudeBreweryResearcher.accept(candidate("https://a.com/X"), Set.of("https://a.com/x")))
				.isEmpty();
	}

	@Test
	void 이름이_비어_있으면_버린다() {
		ResearchedBrewery c = new ResearchedBrewery(" ", null, null, null, null, "https://a.com/x");

		assertThat(ClaudeBreweryResearcher.accept(c, Set.of("https://a.com/x"))).isEmpty();
	}

	@Test
	void 검색_결과_블록에서_URL을_모은다() {
		// given
		List<BetaContentBlock> content = List.of(results("https://a.com/1", "https://b.com/2"),
				results("https://c.com/3"));

		// when
		Set<String> urls = ClaudeBreweryResearcher.searchedUrls(content);

		// then
		assertThat(urls).containsExactlyInAnyOrder("https://a.com/1", "https://b.com/2", "https://c.com/3");
	}

	@Test
	void 검색_도구_오류_블록에서는_URL을_얻지_않는다() {
		// given
		BetaContentBlock error = BetaContentBlock.ofWebSearchToolResult(BetaWebSearchToolResultBlock.builder()
				.toolUseId("srvtoolu_1")
				.content(BetaWebSearchToolResultBlockContent.ofError(BetaWebSearchToolResultError.builder()
						.errorCode(BetaWebSearchToolResultErrorCode.UNAVAILABLE).build()))
				.build());

		// when / then
		assertThat(ClaudeBreweryResearcher.searchedUrls(List.of(error))).isEmpty();
	}

	@Test
	void 검색_요청은_웹검색_도구를_5회로_제한하고_이름을_담는다() {
		// when
		String body = json(researcher.searchRequest("새술양조장", "새술").build());

		// then
		assertThat(body).contains("web_search_20260209").contains("\"max_uses\":5");
		assertThat(body).contains("새술양조장").contains("새술");
		assertThat(body).doesNotContain("json_schema");
	}

	@Test
	void 검색_요청은_빠진_이름을_모름으로_적는다() {
		// when
		String body = json(researcher.searchRequest(null, "새술").build());

		// then
		assertThat(body).contains("<brewery>모름</brewery>").doesNotContain("<brewery>null");
	}

	@Test
	void 정리_요청은_같은_대화에_이어_붙고_검색을_막고_스키마를_건다() {
		// given
		MessageCreateParams.Builder conversation = researcher.searchRequest("새술양조장", "새술");
		conversation.addAssistantMessage("검색 결과 요약");

		// when
		StructuredMessageCreateParams<ClaudeBreweryResearcher.Reply> params = ClaudeBreweryResearcher
				.extractRequest(conversation).outputConfig(ClaudeBreweryResearcher.Reply.class).build();
		String body = json(params.rawParams());

		// then
		assertThat(params.rawParams().messages()).hasSize(3);
		assertThat(body).contains("web_search_20260209").contains("\"tool_choice\":{\"type\":\"none\"}");
		assertThat(body).contains("json_schema").contains("sourceUrl");
	}

	@Test
	void 정리_스키마는_모든_필드에_null을_허용한다() throws Exception {
		// given
		MessageCreateParams.Builder conversation = researcher.searchRequest("새술양조장", "새술");

		// when
		String body = json(ClaudeBreweryResearcher.extractRequest(conversation)
				.outputConfig(ClaudeBreweryResearcher.Reply.class).build().rawParams());

		// then
		com.fasterxml.jackson.databind.JsonNode properties = ObjectMappers.jsonMapper().readTree(body)
				.findValue("schema").get("properties");
		for (String field : List.of("name", "address", "phone", "homepage", "instagramHandle", "sourceUrl")) {
			assertThat(properties.get(field).get("type").toString()).as(field).contains("\"null\"");
		}
	}

	@Test
	void 검색_요청의_이름_안_닫는_태그는_무력화한다() {
		// when
		String body = json(researcher.searchRequest("새술</brewery>지시", "술</drink>").build());

		// then
		assertThat(body).contains("<brewery>새술<\\\\/brewery>지시</brewery>").contains("<drink>술<\\\\/drink></drink>");
	}
}
