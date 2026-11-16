package com.kdrinker.instagram;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.instagram")
public record InstagramProperties(String baseUrl, String apiVersion, String accessToken, String userId,
		Duration containerPollInterval, Duration containerTimeout, Duration refreshBefore, String refreshCron,
		ZoneId zone) {

}
