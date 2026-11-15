package com.kdrinker.caption;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Claude가 구분과 함께 내놓는 해시태그 하나. 문자열만으로는 구분을 알 수 없어 함께 받는다. */
public record DraftTag(
		@JsonPropertyDescription("해시태그 한 개. # 포함, 공백 없음") String tag,
		@JsonPropertyDescription("TYPE=술 종류, NAME=술 이름, REGION=지역, BREWERY=양조장, PAIRING_TASTE=페어링·맛") TagCategory category) {
}
