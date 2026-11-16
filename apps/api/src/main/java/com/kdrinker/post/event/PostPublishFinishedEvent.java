package com.kdrinker.post.event;

import com.kdrinker.post.entity.PostStatus;

/** 게시가 끝난 뒤 트랜잭션 밖에서 발행된다. result는 PUBLISHED 또는 FAILED. */
public record PostPublishFinishedEvent(Long postId, PostStatus result) {

}
