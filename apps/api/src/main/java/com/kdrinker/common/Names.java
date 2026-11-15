package com.kdrinker.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Names {

	private static final Pattern BRACKETS = Pattern.compile("[(\\[{（][^()\\[\\]{}（）]*[)\\]}）]");
	private static final Pattern CORPORATE = Pattern.compile("주식회사|농업회사법인|영농조합법인|유한회사");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private Names() {
	}

	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String s = Normalizer.normalize(raw, Normalizer.Form.NFC).replace("㈜", "");
		String prev;
		do {
			prev = s;
			s = BRACKETS.matcher(s).replaceAll("");
		} while (!s.equals(prev));
		s = CORPORATE.matcher(s).replaceAll("");
		s = WHITESPACE.matcher(s).replaceAll("");
		return s.toLowerCase(Locale.ROOT);
	}

}
