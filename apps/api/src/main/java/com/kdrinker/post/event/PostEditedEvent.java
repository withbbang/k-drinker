package com.kdrinker.post.event;

/**
 * 수정 화면 저장이 검토 대기로 전이한 뒤 저장 트랜잭션 안에서 발행된다.
 * 리스너는 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 으로 받아 새 개요를 보낸다.
 */
public record PostEditedEvent(Long postId) {

}
