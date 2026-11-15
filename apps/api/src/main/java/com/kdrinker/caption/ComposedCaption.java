package com.kdrinker.caption;

/** caption은 마커·해시태그 블록을 포함한 저장용, hashtags는 공백으로 구분한 태그 덩어리. */
public record ComposedCaption(String caption, String hashtags) {
}
