package com.kdrinker.telegram;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(String baseUrl, String botToken, String chatId, int pollTimeoutSeconds,
		Duration albumWindow, Duration sendRetryDelay, int sendMaxRetries) {

}
