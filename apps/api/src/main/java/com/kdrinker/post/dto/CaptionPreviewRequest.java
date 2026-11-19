package com.kdrinker.post.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 저장하지 않은 수정 화면 입력으로 캡션을 채워 보는 요청. drinkId·newDrinkName은 둘 중 하나만 준다. */
public record CaptionPreviewRequest(Long drinkId, @Size(max = 255) String newDrinkName, Long breweryId,
		BigDecimal rating, @NotNull String captionHead, @NotNull String captionTail) {
}
