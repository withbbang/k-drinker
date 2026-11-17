package com.kdrinker.post.dto;

import com.kdrinker.brewery.dto.BreweryRequest;
import com.kdrinker.post.entity.FitMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 수정 화면 저장 요청. 웹은 폼 전체 상태를 보낸다(rating·memo가 null이면 비운다).
 * drinkId·newDrinkName은 둘 중 하나만, 둘 다 없으면 술을 바꾸지 않는다. breweryId가 없으면 양조장을 바꾸지 않는다.
 * photos 순서가 새 순서이고, 빠진 사진은 지운다.
 */
public record PostUpdateRequest(@NotNull Integer version, Long drinkId, @Size(max = 255) String newDrinkName,
		Long breweryId, @Valid BreweryRequest breweryEdit, BigDecimal rating, @Size(max = 2000) String memo,
		@NotNull String captionHead, @NotNull String captionTail, @NotNull List<@Valid PhotoEdit> photos) {

	public record PhotoEdit(@NotNull Long id, @NotNull FitMode fitMode) {
	}
}
