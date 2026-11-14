package com.kdrinker.post.entity;

import static com.kdrinker.post.entity.PostStatus.ANALYZING;
import static com.kdrinker.post.entity.PostStatus.CANCELED;
import static com.kdrinker.post.entity.PostStatus.FAILED;
import static com.kdrinker.post.entity.PostStatus.PENDING_REVIEW;
import static com.kdrinker.post.entity.PostStatus.PUBLISHED;
import static com.kdrinker.post.entity.PostStatus.PUBLISHING;
import static com.kdrinker.post.entity.PostStatus.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostStatusTest {

	private static final Map<PostStatus, Set<PostStatus>> ALLOWED = Map.of(
			RECEIVED, Set.of(ANALYZING, FAILED),
			ANALYZING, Set.of(PENDING_REVIEW, FAILED),
			PENDING_REVIEW, Set.of(PUBLISHING, CANCELED, PENDING_REVIEW),
			PUBLISHING, Set.of(PUBLISHED, FAILED),
			FAILED, Set.of(ANALYZING, PUBLISHING, PENDING_REVIEW),
			PUBLISHED, Set.of(),
			CANCELED, Set.of());

	@Test
	void 허용된_전이만_true다() {
		// given 허용 전이 표

		// when / then 모든 (from, to) 쌍이 표와 일치한다
		for (PostStatus from : PostStatus.values()) {
			for (PostStatus to : PostStatus.values()) {
				assertThat(from.canTransitionTo(to))
						.as("%s -> %s", from, to)
						.isEqualTo(ALLOWED.get(from).contains(to));
			}
		}
	}

	@Test
	void 종료_상태에서는_어디로도_전이할_수_없다() {
		// given
		PostStatus[] terminals = {PUBLISHED, CANCELED};

		// when / then
		for (PostStatus terminal : terminals) {
			for (PostStatus to : PostStatus.values()) {
				assertThat(terminal.canTransitionTo(to)).isFalse();
			}
		}
	}

	@Test
	void 검토_대기에서_바로_게시됨으로_갈_수_없다() {
		// when / then
		assertThat(PENDING_REVIEW.canTransitionTo(PUBLISHED)).isFalse();
	}

}
