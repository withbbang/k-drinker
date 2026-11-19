package com.kdrinker.post.dto;

import com.kdrinker.brewery.dto.BreweryResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * @param publishCaption 실제로 게시될 캡션(저장된 캡션에서 마커만 뺀 값). 개요 화면은 이 값을 그대로 보여 준다.
 * @param retryable 서버가 [다시 시도]를 받아 주는 실패인지. false면 [수정]으로 고쳐야 한다.
 */
public record PostDetailResponse(Long id, String status, int version, boolean editable, String failedStage,
		String failureReason, Long drinkId, String drinkName, BreweryResponse brewery, BigDecimal rating,
		String memo, String captionHead, String breweryBlock, String captionTail, boolean captionEdited,
		int captionLength, List<PostPhotoResponse> photos, String permalink, String channel,
		String publishCaption, boolean retryable, boolean captionTemplated) {
}
