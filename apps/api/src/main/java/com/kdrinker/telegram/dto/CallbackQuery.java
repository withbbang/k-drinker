package com.kdrinker.telegram.dto;

public record CallbackQuery(String id, long fromChatId, Long messageId, String data) {

}
