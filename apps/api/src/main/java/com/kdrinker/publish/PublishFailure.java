package com.kdrinker.publish;

/** 게시 실패 사유. message는 post.failure_reason에 그대로 저장되고 텔레그램에 보인다. */
public enum PublishFailure {
	IMAGE_FETCH("인스타그램이 이미지를 가져가지 못했습니다."),
	AUTH("인스타그램 인증에 문제가 있습니다. 토큰을 확인해 주세요."),
	RATE_LIMIT("오늘 게시 한도를 넘었습니다. 내일 다시 시도해 주세요."),
	CAPTION_TOO_LONG("캡션이 2,200자를 넘었습니다."),
	UNKNOWN("알 수 없는 오류가 났습니다. 서버 로그를 확인해 주세요.");

	private final String message;

	PublishFailure(String message) {
		this.message = message;
	}

	public String message() {
		return message;
	}
}
