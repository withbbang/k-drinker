package com.kdrinker.caption;

/** 받침에 따라 조사 표기("은(는)" 등)에서 알맞은 쪽을 고른다. */
public final class Josa {

	private static final int HANGUL_START = 0xAC00;
	private static final int HANGUL_END = 0xD7A3;
	private static final int JONG_RIEUL = 8;
	private static final String BATCHIM_DIGITS = "013678";
	/** 일·칠·팔은 ㄹ 받침이다. */
	private static final String RIEUL_DIGITS = "178";

	private Josa() {
	}

	/** pair는 "은(는)"처럼 받침 있을 때(괄호 밖)와 없을 때(괄호 안)를 한 쌍으로 쓴 표기. 고른 쪽만 돌려준다. */
	public static String pick(String word, String pair) {
		int open = pair.indexOf('(');
		String withBatchim = pair.substring(0, open);
		String without = pair.substring(open + 1, pair.length() - 1);
		boolean rieulAsNone = "으로(로)".equals(pair);
		return switch (batchim(word)) {
			case NONE -> without;
			case RIEUL -> rieulAsNone ? without : withBatchim;
			case OTHER -> withBatchim;
		};
	}

	private enum Batchim { NONE, RIEUL, OTHER }

	private static Batchim batchim(String word) {
		if (word == null || word.isEmpty()) {
			return Batchim.NONE;
		}
		int cp = word.codePointBefore(word.length());
		if (cp >= HANGUL_START && cp <= HANGUL_END) {
			int jong = (cp - HANGUL_START) % 28;
			if (jong == 0) {
				return Batchim.NONE;
			}
			return jong == JONG_RIEUL ? Batchim.RIEUL : Batchim.OTHER;
		}
		if (cp < 128 && BATCHIM_DIGITS.indexOf(cp) >= 0) {
			return RIEUL_DIGITS.indexOf(cp) >= 0 ? Batchim.RIEUL : Batchim.OTHER;
		}
		return Batchim.NONE;
	}
}
