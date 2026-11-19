package com.kdrinker.caption;

import com.kdrinker.brewery.entity.Brewery;
import java.util.ArrayList;
import java.util.List;

/** 캡션의 양조장 블록. 마커로 앞뒤를 감싼다. */
public final class BreweryBlock {

	public static final String MARKER = "<!--brewery-->";

	/** 새 캡션의 양조장 블록 틀. 기타 SNS 줄에는 이모지를 붙이지 않는다. */
	public static final String TEMPLATE = String.join("\n", MARKER, "🍶 {양조장}", "📍 {주소}", "☎ {전화}", "🏠 {홈페이지}",
			"📷 {인스타그램}", "{기타SNS}", MARKER);

	private BreweryBlock() {
	}

	public static String render(Brewery b) {
		List<String> lines = new ArrayList<>();
		lines.add(MARKER);
		if (b != null) {
			add(lines, "🍶 ", b.getName());
			add(lines, "📍 ", b.getAddress());
			add(lines, "☎ ", b.getPhone());
			add(lines, "🏠 ", homepage(b.getHomepage()));
			add(lines, "📷 @", handle(b.getInstagramHandle()));
		}
		lines.add(MARKER);
		return String.join("\n", lines);
	}

	private static void add(List<String> lines, String prefix, String value) {
		if (value != null && !value.isBlank()) {
			lines.add(prefix + value);
		}
	}

	static String homepage(String h) {
		if (h == null) {
			return null;
		}
		return h.strip().replaceFirst("(?i)^https?://", "").replaceFirst("/+$", "");
	}

	static String handle(String h) {
		return h == null ? null : h.strip().replaceFirst("^@+", "");
	}
}
