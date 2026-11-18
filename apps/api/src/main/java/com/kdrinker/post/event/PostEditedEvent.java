package com.kdrinker.post.event;

/**
 * 수정 화면 저장이 검토 대기로 전이한 뒤 저장 트랜잭션 안에서 발행된다.
 * 리스너는 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 으로 받는다.
 *
 * @param handedOverToWeb 이번 저장으로 텔레그램 담당에서 웹 담당으로 넘어왔는지. 그렇다면 텔레그램 개요 버튼을 지운다.
 */
public record PostEditedEvent(Long postId, boolean handedOverToWeb) {

}
