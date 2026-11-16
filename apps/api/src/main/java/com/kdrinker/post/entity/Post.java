package com.kdrinker.post.entity;

import com.kdrinker.drink.entity.Drink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 후기 게시물. status·version·updatedAt 은 {@code PostRepository.transition} 이 조건부 UPDATE 로만 바꾼다.
 */
@Getter
@Entity
@Table(name = "post")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "drink_id")
	private Drink drink;

	@Column(precision = 2, scale = 1)
	private BigDecimal rating;

	@Column(length = 2000)
	private String memo;

	@Column(name = "taste_notes", length = 1000)
	private String tasteNotes;

	@Column(length = 1000)
	private String pairing;

	@Column(length = 4000)
	private String caption;

	@Column(name = "caption_edited")
	private boolean captionEdited;

	@Column(length = 500)
	private String hashtags;

	/** transition 의 조건부 UPDATE 로만 바뀐다. 오래된 인스턴스가 덮어쓰지 못하도록 updatable=false. */
	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private PostStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "failed_stage")
	private PostStatus failedStage;

	@Column(name = "failure_reason", length = 1000)
	private String failureReason;

	@Column(updatable = false)
	private int version;

	@Enumerated(EnumType.STRING)
	private PostSource source;

	@Column(name = "telegram_message_id")
	private Long telegramMessageId;

	@Column(name = "ig_media_id")
	private String igMediaId;

	private String permalink;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "created_at")
	private Instant createdAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "updated_at")
	private Instant updatedAt;

	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "published_at")
	private Instant publishedAt;

	public static Post receive(PostSource source, String memo, BigDecimal rating, Clock clock) {
		Post post = new Post();
		Instant now = clock.instant();
		post.source = source;
		post.memo = memo;
		post.rating = rating;
		post.status = PostStatus.RECEIVED;
		post.version = 0;
		post.createdAt = now;
		post.updatedAt = now;
		return post;
	}

	public void linkTelegramMessage(Long telegramMessageId) {
		this.telegramMessageId = telegramMessageId;
	}

	public void assignDrink(Drink drink) {
		this.drink = drink;
	}

	public void recordRating(BigDecimal rating) {
		this.rating = rating;
	}

	public void writeCaption(String caption, String hashtags, String tasteNotes, String pairing) {
		this.caption = caption;
		this.hashtags = hashtags;
		this.tasteNotes = tasteNotes;
		this.pairing = pairing;
	}

	public void editCaption(String caption) {
		this.caption = caption;
		this.captionEdited = true;
	}

	public void recordFailure(PostStatus stage, String reason) {
		this.failedStage = stage;
		this.failureReason = reason;
	}

	public void clearFailure() {
		this.failedStage = null;
		this.failureReason = null;
	}

	public void recordPublished(String igMediaId, String permalink, Instant publishedAt) {
		this.igMediaId = igMediaId;
		this.permalink = permalink;
		this.publishedAt = publishedAt;
	}

}
