package com.kdrinker.instagram.service;

import java.time.Instant;

/** 토큰 값은 담지 않는다. */
public record TokenStatus(Instant expiresAt, Instant refreshedAt) {

}
