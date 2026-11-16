package com.kdrinker.telegram;

/** 텔레그램 호출 실패. 메시지에 토큰이 들어간 URL을 담지 않는다. */
public class TelegramApiException extends RuntimeException {

	public TelegramApiException(String message) {
		super(message);
	}

}
