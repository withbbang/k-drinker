package com.kdrinker.telegram.dto;

import java.util.List;

public record TelegramMessage(long messageId, long chatId, String text, String caption, String mediaGroupId,
		List<PhotoSize> photo, Document document) {

}
