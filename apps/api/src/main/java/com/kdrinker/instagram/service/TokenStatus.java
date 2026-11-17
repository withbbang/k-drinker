package com.kdrinker.instagram.service;

import java.time.Instant;

/** 토큰 값은 담지 않는다. expiresAt이 null이면 만료되지 않는 토큰이고, refreshedAt은 마지막으로 저장한 시각이다. */
public record TokenStatus(Instant expiresAt, Instant refreshedAt) {

}
