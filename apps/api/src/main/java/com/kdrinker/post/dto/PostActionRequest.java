package com.kdrinker.post.dto;

import jakarta.validation.constraints.NotNull;

/** 웹의 확인·취소·재시도 요청. 화면이 읽은 게시물 버전을 보낸다. */
public record PostActionRequest(@NotNull Integer version) {
}
