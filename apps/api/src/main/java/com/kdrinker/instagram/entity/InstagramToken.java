package com.kdrinker.instagram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 인스타그램 액세스 토큰. 사용자가 한 명이라 행도 하나(id=1)다. */
@Getter
@Entity
@Table(name = "instagram_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstagramToken {

	public static final int SINGLE_ROW_ID = 1;

	@Id
	private Integer id;

	@Column(name = "access_token")
	private String accessToken;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "expires_at")
	private Instant expiresAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "refreshed_at")
	private Instant refreshedAt;

	public static InstagramToken issue(String accessToken, Instant expiresAt, Instant now) {
		InstagramToken token = new InstagramToken();
		token.id = SINGLE_ROW_ID;
		token.accessToken = accessToken;
		token.expiresAt = expiresAt;
		token.refreshedAt = now;
		return token;
	}

	public void refresh(String accessToken, Instant expiresAt, Instant now) {
		this.accessToken = accessToken;
		this.expiresAt = expiresAt;
		this.refreshedAt = now;
	}

}
