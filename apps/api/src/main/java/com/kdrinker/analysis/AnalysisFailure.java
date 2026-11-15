package com.kdrinker.analysis;

/** 분석 실패 사유. message는 post.failure_reason에 그대로 저장되고 텔레그램에 보인다. */
public enum AnalysisFailure {
	LABEL_UNREADABLE("사진에서 술 이름을 읽지 못했습니다."),
	CLAUDE_UNAVAILABLE("분석 서버 응답에 문제가 있었습니다."),
	UNKNOWN("알 수 없는 오류가 났습니다. 서버 로그를 확인해 주세요.");

	private final String message;

	AnalysisFailure(String message) {
		this.message = message;
	}

	public String message() {
		return message;
	}
}
