package com.kdrinker.post.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PostSummaryResponse(Long id, String status, String drinkName, BigDecimal rating,
		String thumbnailUrl, String permalink, Instant createdAt) {
}
