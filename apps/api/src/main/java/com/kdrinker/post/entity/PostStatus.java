package com.kdrinker.post.entity;

import java.util.Set;

public enum PostStatus {
	RECEIVED, ANALYZING, PENDING_REVIEW, PUBLISHING, PUBLISHED, FAILED, CANCELED;

	public boolean canTransitionTo(PostStatus next) {
		return allowedNext().contains(next);
	}

	private Set<PostStatus> allowedNext() {
		return switch (this) {
			case RECEIVED -> Set.of(ANALYZING, FAILED);
			case ANALYZING -> Set.of(PENDING_REVIEW, FAILED);
			case PENDING_REVIEW -> Set.of(PUBLISHING, CANCELED, PENDING_REVIEW, FAILED);
			case PUBLISHING -> Set.of(PUBLISHED, FAILED);
			case FAILED -> Set.of(ANALYZING, PUBLISHING, PENDING_REVIEW);
			case PUBLISHED, CANCELED -> Set.of();
		};
	}
}
