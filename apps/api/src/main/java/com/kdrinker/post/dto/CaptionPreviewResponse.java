package com.kdrinker.post.dto;

/** 이름표를 채운 캡션, 그 글자 수, 이름표 캡션 여부. */
public record CaptionPreviewResponse(String caption, int length, boolean templated) {
}
