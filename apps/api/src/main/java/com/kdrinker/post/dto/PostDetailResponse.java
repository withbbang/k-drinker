package com.kdrinker.post.dto;

import com.kdrinker.brewery.dto.BreweryResponse;
import java.math.BigDecimal;
import java.util.List;

public record PostDetailResponse(Long id, String status, int version, boolean editable, String failedStage,
		String failureReason, Long drinkId, String drinkName, BreweryResponse brewery, BigDecimal rating,
		String memo, String captionHead, String breweryBlock, String captionTail, boolean captionEdited,
		int captionLength, List<PostPhotoResponse> photos, String permalink) {
}
