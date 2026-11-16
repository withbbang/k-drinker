package com.kdrinker.instagram;

import com.kdrinker.publish.PublishFailure;
import lombok.Getter;

/** 인스타그램 호출 실패. 메시지에 액세스 토큰을 담지 않는다. */
@Getter
public class InstagramApiException extends RuntimeException {

	private final int code;
	private final Integer subcode;

	public InstagramApiException(int code, Integer subcode, String message) {
		super(message);
		this.code = code;
		this.subcode = subcode;
	}

	/** Meta 오류 코드를 게시 실패 사유로 분류한다. */
	public PublishFailure classify() {
		if (subcode != null && subcode == 2207042) {
			return PublishFailure.RATE_LIMIT;
		}
		if (subcode != null && (subcode == 2207052 || subcode == 2207003)) {
			return PublishFailure.IMAGE_FETCH;
		}
		if (code == 190 || code == 10 || (code >= 200 && code <= 299)) {
			return PublishFailure.AUTH;
		}
		if (code == 4 || code == 17 || code == 32 || code == 613) {
			return PublishFailure.RATE_LIMIT;
		}
		return PublishFailure.UNKNOWN;
	}

}
