package com.kdrinker.post.event;

/**
 * 양조장 수정으로 대기 게시물이 텔레그램 담당에서 웹 담당으로 넘어갔을 때, 수정 트랜잭션 안에서 발행된다.
 * 리스너는 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 으로 받는다.
 */
public record PostHandedOverEvent(Long postId) {

}
