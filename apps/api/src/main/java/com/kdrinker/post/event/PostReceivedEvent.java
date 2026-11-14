package com.kdrinker.post.event;

/**
 * 게시물이 접수되어 사진 저장까지 끝났을 때 접수 트랜잭션 안에서 발행된다.
 * 리스너는 커밋 전 행을 읽지 않도록 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 으로 받는다 (P2).
 */
public record PostReceivedEvent(Long postId) {

}
