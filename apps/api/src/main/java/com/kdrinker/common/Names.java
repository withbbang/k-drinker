package com.kdrinker.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Names {

	private static final Pattern BRACKETS = Pattern.compile("[(\\[{（][^()\\[\\]{}（）]*[)\\]}）]");
	private static final Pattern CORPORATE = Pattern.compile("주식회사|농업회사법인|영농조합법인|유한회사");
	/** ASCII 공백뿐 아니라 줄바꿈하지 않는 공백(U+00A0)·전각 공백(U+3000) 등 유니코드 공백 문자(Z*)도 지운다. */
	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

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
