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

/** 인스타그램 액세스 토큰. 사용자가 한 명이라 행도 하나(id=1)다. expiresAt이 null이면 만료되지 않는 토큰이다. */
@Getter
@Entity
@Table(name = "instagram_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstagramToken {

	public static final int SINGLE_ROW_ID = 1;

	@Id
	private Integer id;

	@Column(name = "access_token", length = 1000)
	private String accessToken;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "expires_at")
	private Instant expiresAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "refreshed_at")
	private Instant refreshedAt;

	/** 만료 없는 토큰으로 행을 만든다. */
	public static InstagramToken issue(String accessToken, Instant now) {
		InstagramToken token = new InstagramToken();
		token.id = SINGLE_ROW_ID;
		token.replace(accessToken, now);
		return token;
	}

	/** 토큰을 바꾸고, 예전 갱신 방식 시절의 만료 시각도 비운다. */
	public void replace(String accessToken, Instant now) {
		this.accessToken = accessToken;
		this.expiresAt = null;
		this.refreshedAt = now;
	}

}
