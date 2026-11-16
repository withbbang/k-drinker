package com.kdrinker.telegram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보낸 알림 기록. dedup_key 유니크가 같은 알림의 중복 전송을 막는다. */
@Getter
@Entity
@Table(name = "telegram_notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dedup_key")
	private String dedupKey;

	@Column(name = "post_id")
	private Long postId;

	@Enumerated(EnumType.STRING)
	private NotificationKind kind;

	@Column(name = "message_id")
	private Long messageId;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	public TelegramNotification(String dedupKey, Long postId, NotificationKind kind, LocalDateTime sentAt) {
		this.dedupKey = dedupKey;
		this.postId = postId;
		this.kind = kind;
		this.sentAt = sentAt;
	}

	public void recordMessageId(long messageId) {
		this.messageId = messageId;
	}

}
