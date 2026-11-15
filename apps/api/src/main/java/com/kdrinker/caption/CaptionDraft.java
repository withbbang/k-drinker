package com.kdrinker.caption;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Claude가 만든 문장과 태그. 블록 조립·양조장 블록·별점은 코드가 한다. */
public record CaptionDraft(
		@JsonPropertyDescription("첫 줄 훅. 한 줄, 40자 안팎") String hook,
		@JsonPropertyDescription("술 소개 1~2문장") String intro,
		@JsonPropertyDescription("맛·페어링 1~3문장. 메모에 없으면 빈 문자열") String tasting,
		@JsonPropertyDescription("구분이 붙은 해시태그 목록") List<DraftTag> hashtags) {
}
