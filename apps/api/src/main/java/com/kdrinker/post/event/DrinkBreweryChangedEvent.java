package com.kdrinker.post.event;

/**
 * 웹 수정 저장에서 술의 양조장을 바꿨을 때 그 저장 트랜잭션 안에서(저장한 게시물의 전이 뒤) 발행된다.
 * 같은 술을 쓰는 다른 대기 게시물의 캡션이 바뀌므로 양조장 수정처럼 처리한다. savedPostId(저장한 게시물)는 뺀다.
 */
public record DrinkBreweryChangedEvent(Long drinkId, Long breweryId, Long savedPostId) {
}
