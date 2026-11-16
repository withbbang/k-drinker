package com.kdrinker.instagram.dto;

import com.kdrinker.instagram.service.TokenStatus;
import java.time.Instant;

public record InstagramTokenResponse(Instant expiresAt, Instant refreshedAt) {

	public static InstagramTokenResponse from(TokenStatus status) {
		return new InstagramTokenResponse(status.expiresAt(), status.refreshedAt());
	}

}
