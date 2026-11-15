package com.kdrinker.analysis.brewery;

import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaToolChoiceNone;
import com.anthropic.models.beta.messages.BetaWebSearchResultBlock;
import com.anthropic.models.beta.messages.BetaWebSearchTool20260209;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kdrinker.analysis.claude.ClaudeCalls;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Claude 웹 검색으로 양조장 정보를 찾는다. 두 단계다: ① 웹 검색 도구를 켠 텍스트 요청(pause_turn이면 같은 대화로 최대 2회 이어감),
 * ② 같은 대화에 정리 요청을 붙여 structured output으로 받는다. 구조화 출력은 인용(citations)과 함께 쓸 수 없고 웹 검색 응답은
 * 인용을 달기 때문에 한 요청으로 합치지 않는다. 출처 URL이 ①의 검색 결과에 없으면 버린다.
 */
@Component
public class ClaudeBreweryResearcher implements BreweryResearcher {

	static final long MAX_SEARCHES = 5L;
	static final int MAX_CONTINUATIONS = 2;

	static final String SYSTEM = """
			너는 한국 전통주 양조장 정보를 웹에서 찾는 조사원이다.
			주어진 양조장 이름과 술 이름으로 웹 검색을 해서 그 양조장의 정식 이름, 주소, 전화번호, 홈페이지, 인스타그램 계정을 찾는다.
			- 검색 결과 페이지에서 직접 확인한 값만 적는다. 추측하거나 지어내지 않는다.
			- 인스타그램 계정은 출처 페이지에 적혀 있는 것만 적는다.
			- 값마다 어느 검색 결과 URL에서 확인했는지 함께 적는다.
			- 같은 이름의 다른 양조장과 헷갈리면 술 이름으로 구분하고, 끝내 구분되지 않으면 찾지 못했다고 답한다.""";

	static final String EXTRACT = """
			위 검색 결과만 근거로 양조장 정보를 정리하라.
			- sourceUrl은 위 검색 결과에 실제로 나온 URL 하나를 그대로 적는다. 정보를 가장 많이 확인한 페이지를 고른다.
			- 확인하지 못한 값은 비워 둔다. 양조장을 찾지 못했으면 name과 sourceUrl을 비워 둔다.
			- instagramHandle은 출처 페이지에서 확인한 계정만, @ 없이 적는다.""";

	private final ClaudeCalls calls;

	public ClaudeBreweryResearcher(ClaudeCalls calls) {
		this.calls = calls;
	}

	@Override
	public Optional<ResearchedBrewery> research(String breweryName, String drinkName) {
		MessageCreateParams.Builder conversation = searchRequest(breweryName, drinkName);
		Set<String> urls = new LinkedHashSet<>();
		BetaMessage reply = calls.create(conversation.build());
		for (int continued = 0; ; continued++) {
			urls.addAll(searchedUrls(reply.content()));
			conversation.addMessage(reply);
			if (!reply.stopReason().map(BetaStopReason.PAUSE_TURN::equals).orElse(false)) {
				break;
			}
			if (continued >= MAX_CONTINUATIONS) {
				return Optional.empty();
			}
			reply = calls.create(conversation.build());
		}
		if (urls.isEmpty()) {
			return Optional.empty();
		}
		Reply r = calls.structured(extractRequest(conversation), Reply.class);
		return accept(r == null ? null : r.toResearched(), urls);
	}

	/** ① 검색 요청: 웹 검색 도구(최대 5회) + 이름. 구조화 출력은 걸지 않는다. */
	MessageCreateParams.Builder searchRequest(String breweryName, String drinkName) {
		String text = "<brewery>" + orUnknown(breweryName) + "</brewery>\n<drink>" + orUnknown(drinkName) + "</drink>";
		return calls.base()
				.system(SYSTEM)
				.addTool(BetaWebSearchTool20260209.builder().maxUses(MAX_SEARCHES).build())
				.addUserMessage(text);
	}

	/** ② 정리 요청: 같은 대화에 사용자 메시지를 붙이고 추가 검색은 막는다(도구 정의는 기록 때문에 유지). */
	static MessageCreateParams.Builder extractRequest(MessageCreateParams.Builder conversation) {
		return conversation.toolChoice(BetaToolChoiceNone.builder().build()).addUserMessage(EXTRACT);
	}

	/** web_search_tool_result 블록 중 결과 목록인 것에서만 URL을 모은다. 오류 객체는 건너뛴다. */
	static Set<String> searchedUrls(List<BetaContentBlock> content) {
		return content.stream()
				.flatMap(block -> block.webSearchToolResult().stream())
				.flatMap(result -> result.content().resultBlocks().stream())
				.flatMap(List::stream)
				.map(BetaWebSearchResultBlock::url)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** 이름이 있고 sourceUrl이 실제 검색 결과 URL과 같을 때만 받는다(앞뒤 공백·끝 `/` 하나·호스트 대소문자 무시). */
	static Optional<ResearchedBrewery> accept(ResearchedBrewery candidate, Set<String> searchedUrls) {
		if (candidate == null || isBlank(candidate.name()) || isBlank(candidate.sourceUrl())) {
			return Optional.empty();
		}
		String source = normalizeUrl(candidate.sourceUrl());
		boolean searched = searchedUrls.stream().anyMatch(url -> normalizeUrl(url).equals(source));
		return searched ? Optional.of(candidate) : Optional.empty();
	}

	static String normalizeUrl(String url) {
		String s = url.strip();
		if (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		try {
			String host = new URI(s).getHost();
			int at = host == null ? -1 : s.indexOf(host);
			if (at >= 0) {
				s = s.substring(0, at) + host.toLowerCase(Locale.ROOT) + s.substring(at + host.length());
			}
		} catch (URISyntaxException e) {
			// 파싱이 안 되는 URL(인코딩 안 된 한글 등)은 그대로 비교한다
		}
		return s;
	}

	private static String orUnknown(String s) {
		return isBlank(s) ? "모름" : s.strip();
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static String clean(String s) {
		return isBlank(s) ? null : s.strip();
	}

	record Reply(
			@JsonPropertyDescription("양조장 정식 이름. 찾지 못했으면 비움") String name,
			@JsonPropertyDescription("주소. 출처에서 확인한 것만") String address,
			@JsonPropertyDescription("전화번호. 출처에서 확인한 것만") String phone,
			@JsonPropertyDescription("홈페이지 URL. 출처에서 확인한 것만") String homepage,
			@JsonPropertyDescription("인스타그램 계정. 출처 페이지에서 확인한 것만, @ 없이") String instagramHandle,
			@JsonPropertyDescription("정보를 확인한 검색 결과 URL 하나. 위 검색 결과에 나온 그대로") String sourceUrl) {

		ResearchedBrewery toResearched() {
			return new ResearchedBrewery(clean(name), clean(address), clean(phone), clean(homepage),
					clean(instagramHandle), clean(sourceUrl));
		}
	}
}
