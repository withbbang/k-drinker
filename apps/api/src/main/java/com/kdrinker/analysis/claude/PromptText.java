package com.kdrinker.analysis.claude;

/** 사용자 메시지의 데이터 구획(&lt;memo&gt; 등)에 값을 넣을 때 쓰는 공통 처리. */
public final class PromptText {

	private PromptText() {
	}

	/** 값 안의 닫는 태그({@code </})가 데이터 구획을 끊지 못하게 {@code <\/}로 바꾼다. null은 빈 문자열. */
	public static String neutral(String s) {
		return s == null ? "" : s.replace("</", "<\\/");
	}
}
