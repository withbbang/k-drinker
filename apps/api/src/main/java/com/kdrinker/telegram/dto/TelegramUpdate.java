package com.kdrinker.telegram.dto;

public record TelegramUpdate(long updateId, TelegramMessage message, CallbackQuery callbackQuery) {

}
