package com.kdrinker.analysis.claude;

/** Claude를 쓸 수 없는 모든 경우(키 없음, 거절, 토큰 초과, SDK·네트워크 오류, 응답 해석 실패). */
public class ClaudeUnavailableException extends RuntimeException {

	public ClaudeUnavailableException(String message) {
		super(message);
	}

	public ClaudeUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
