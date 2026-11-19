package com.kdrinker.caption;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 저장된 캡션의 {이름표}를 보여주거나 게시할 때 값으로 채운다. */
public final class Placeholders {

	public static final List<String> NAMES = List.of("술이름", "술이름태그", "종류", "도수", "용량", "별점", "양조장", "양조장태그",
			"주소", "전화", "홈페이지", "인스타그램", "기타SNS");

	private static final Pattern TOKEN = Pattern.compile("\\{(" + String.join("|", NAMES) + ")\\}"
			+ "(은\\(는\\)|이\\(가\\)|을\\(를\\)|과\\(와\\)|으로\\(로\\)|이에요\\(예요\\))?");

	private Placeholders() {
	}

	public static boolean isTemplate(String stored) {
		return stored != null && NAMES.stream().anyMatch(n -> stored.contains("{" + n + "}"));
	}

	public static String fill(String stored, CaptionValues v) {
		if (!isTemplate(stored)) {
			return stored;
		}
		Map<String, String> values = v.asMap();
		Set<String> seenTags = new HashSet<>();
		List<String> out = new ArrayList<>();
		for (String line : stored.split("\n", -1)) {
			if (line.contains(BreweryBlock.MARKER)) {
				out.add(line);
				continue;
			}
			String filled = fillLine(line, values, seenTags);
			if (filled != null) {
				out.add(filled);
			}
		}
		return String.join("\n", out).replaceAll("\n{3,}", "\n\n");
	}

	/** 줄을 채운다. 줄 전체를 빼야 하면 null. */
	private static String fillLine(String line, Map<String, String> values, Set<String> seenTags) {
		if (!TOKEN.matcher(line).find()) {
			return line;
		}
		List<String> words = new ArrayList<>();
		for (String w : line.split(" ", -1)) {
			if (w.startsWith("#") && hasMissing(w, values)) {
				continue;
			}
			words.add(w);
		}
		String rest = String.join(" ", words);
		if (rest.isBlank() || hasMissing(rest, values)) {
			return null;
		}
		String filled = replace(rest, values);
		String[] parts = filled.split(" ", -1);
		boolean allTags = true;
		for (String p : parts) {
			allTags &= p.startsWith("#");
		}
		if (!allTags) {
			return filled;
		}
		List<String> kept = new ArrayList<>();
		for (String p : parts) {
			if (seenTags.add(p)) {
				kept.add(p);
			}
		}
		return kept.isEmpty() ? null : String.join(" ", kept);
	}

	private static boolean hasMissing(String text, Map<String, String> values) {
		Matcher m = TOKEN.matcher(text);
		while (m.find()) {
			if (!values.containsKey(m.group(1))) {
				return true;
			}
		}
		return false;
	}

	private static String replace(String text, Map<String, String> values) {
		Matcher m = TOKEN.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String value = values.get(m.group(1));
			String josa = m.group(2) == null ? "" : Josa.pick(value, m.group(2));
			m.appendReplacement(sb, Matcher.quoteReplacement(value + josa));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
