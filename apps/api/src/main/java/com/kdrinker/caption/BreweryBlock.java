package com.kdrinker.caption;

import com.kdrinker.brewery.entity.Brewery;
import java.util.ArrayList;
import java.util.List;

/** 캡션의 양조장 블록. 마커로 앞뒤를 감싼다. */
public final class BreweryBlock {

	public static final String MARKER = "<!--brewery-->";

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

	private static String homepage(String h) {
		if (h == null) {
			return null;
		}
		return h.strip().replaceFirst("(?i)^https?://", "").replaceFirst("/+$", "");
	}

	private static String handle(String h) {
		return h == null ? null : h.strip().replaceFirst("^@+", "");
	}
}
