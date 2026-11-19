package com.kdrinker.caption;

import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 해시태그 규칙: 고정 6개 + 종류 0~1 + 이름 1 + 지역 0~1 + 양조장 0~1 + 페어링·맛 0~2 = 12개 이하. */
public final class Hashtags {

	public static final List<String> FIXED = List.of("#전통주", "#우리술", "#전통주후기", "#막걸리", "#전통주추천", "#막걸리추천");
	public static final int MAX = 12;
	static final int MAX_PAIRING_TASTE = 2;
	/** `#`을 뺀 태그 한 개의 최대 글자 수(코드 포인트). 12개를 이어도 post.hashtags(VARCHAR(500))에 들어간다. */
	public static final int MAX_TAG_LENGTH = 30;

	/** 이름·양조장 태그는 이름표로 저장하고 게시·표시할 때 채운다. */
	public static final String NAME_TAG = "#{술이름태그}";
	public static final String BREWERY_TAG = "#{양조장태그}";

	private Hashtags() {
	}

	/**
	 * Claude 태그를 정리해 구성표대로 놓는다. 분류는 Claude가 준 category를 따른다. 양조장이 없으면 지역·양조장 태그를
	 * 버린다. 이름·양조장 태그는 Claude의 NAME·BREWERY 태그를 쓰지 않고 이름표로 둔다(술 이름·양조장이 있고 태그로 쓸 수 있을 때).
	 */
	public static HashtagSet finalize(List<DraftTag> fromClaude, LabelResult label, Brewery brewery) {
		List<DraftTag> tags = fromClaude == null ? List.of() : fromClaude;
		Set<String> seen = new HashSet<>(FIXED);
		String type = first(tags, TagCategory.TYPE, seen);
		String name = label != null && normalize(label.drinkName()) != null ? NAME_TAG : null;
		String region = brewery == null ? null : first(tags, TagCategory.REGION, seen);
		String breweryTag = brewery != null && normalize(brewery.getName()) != null ? BREWERY_TAG : null;
		List<String> pairing = new ArrayList<>();
		for (DraftTag t : tags) {
			if (pairing.size() >= MAX_PAIRING_TASTE) {
				break;
			}
			if (t != null && t.category() == TagCategory.PAIRING_TASTE) {
				String c = claim(t.tag(), seen);
				if (c != null) {
					pairing.add(c);
				}
			}
		}
		return new HashtagSet(FIXED, type, name, region, breweryTag, List.copyOf(pairing));
	}

	private static String first(List<DraftTag> tags, TagCategory category, Set<String> seen) {
		for (DraftTag t : tags) {
			if (t != null && t.category() == category) {
				String c = claim(t.tag(), seen);
				if (c != null) {
					return c;
				}
			}
		}
		return null;
	}

	/** 정규화한 태그가 비지 않고 처음 보는 것이면 seen에 넣고 돌려준다. */
	private static String claim(String raw, Set<String> seen) {
		String n = normalize(raw);
		return n != null && seen.add(n) ? n : null;
	}

	/** 글자·숫자·밑줄만 남기고 `#`을 앞에 붙인다. 남는 게 없거나 {@link #MAX_TAG_LENGTH}자를 넘으면 null. */
	static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		raw.codePoints().filter(cp -> Character.isLetterOrDigit(cp) || cp == '_').forEach(sb::appendCodePoint);
		if (sb.isEmpty() || sb.codePointCount(0, sb.length()) > MAX_TAG_LENGTH) {
			return null;
		}
		return "#" + sb;
	}
}
