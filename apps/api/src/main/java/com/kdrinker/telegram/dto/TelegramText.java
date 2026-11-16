package com.kdrinker.telegram.dto;

import java.util.List;

/** 텔레그램으로 보낼 HTML 본문과 인라인 버튼 줄들. keyboard가 비면 버튼 없이 보낸다. */
public record TelegramText(String html, List<List<InlineButton>> keyboard) {

	public static TelegramText of(String html) {
		return new TelegramText(html, List.of());
	}

}
