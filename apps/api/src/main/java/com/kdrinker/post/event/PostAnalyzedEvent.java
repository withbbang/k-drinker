package com.kdrinker.post.event;

import com.kdrinker.post.entity.PostStatus;

/**
 * 분석이 끝나 상태가 바뀐 뒤(마지막 트랜잭션 커밋 후, 트랜잭션 밖에서) 발행된다. result는 PENDING_REVIEW 또는 FAILED.
 * 트랜잭션 밖에서 발행되므로 받는 쪽은 {@code @EventListener}(또는 fallbackExecution=true)로 받는다.
 */
public record PostAnalyzedEvent(Long postId, PostStatus result) {

}
