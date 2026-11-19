package com.kdrinker.telegram.dto;

import java.util.List;

/** 텔레그램으로 보낼 HTML 본문과 인라인 버튼 줄들, 답장 대상. keyboard가 비면 버튼 없이, replyToMessageId가 null이면 답장 없이 보낸다. */
public record TelegramText(String html, List<List<InlineButton>> keyboard, Long replyToMessageId) {

	public TelegramText(String html, List<List<InlineButton>> keyboard) {
		this(html, keyboard, null);
	}

	public static TelegramText of(String html) {
		return new TelegramText(html, List.of());
	}

	/** 지정한 메시지에 답장으로 보낸다. null이면 답장 없이 보낸다. */
	public TelegramText withReplyTo(Long messageId) {
		return new TelegramText(html, keyboard, messageId);
	}

}
