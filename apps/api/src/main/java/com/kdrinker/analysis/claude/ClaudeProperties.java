package com.kdrinker.analysis.claude;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Claude API 설정. apiKey가 비어 있으면 클라이언트를 만들지 않고, 호출 시점에 실패한다. */
@ConfigurationProperties("app.anthropic")
public record ClaudeProperties(String apiKey, String model, Duration timeout, int maxRetries) {

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}
}
