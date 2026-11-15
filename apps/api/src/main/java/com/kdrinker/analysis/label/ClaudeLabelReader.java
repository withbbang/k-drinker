package com.kdrinker.analysis.label;

import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kdrinker.analysis.claude.ClaudeCalls;
import com.kdrinker.media.FocusBox;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Claude에 사진과 메모를 보내 LabelResult를 받는다. */
@Component
public class ClaudeLabelReader implements LabelReader {

	private final ClaudeCalls calls;

	public ClaudeLabelReader(ClaudeCalls calls) {
		this.calls = calls;
	}

	@Override
	public LabelResult read(List<byte[]> images, String memo) {
		Reply reply = calls.structured(buildRequest(images, memo), Reply.class);
		return toResult(reply);
	}

	/** 이미지 블록들 뒤에 메모 텍스트 블록을 둔 사용자 메시지. 시스템 프롬프트에는 메모가 없다. */
	MessageCreateParams.Builder buildRequest(List<byte[]> images, String memo) {
		List<BetaContentBlockParam> content = new ArrayList<>();
		for (byte[] jpeg : images) {
			content.add(ClaudeCalls.imageBlock(jpeg));
		}
		String text = memo == null || memo.isBlank()
				? "<memo>메모 없음</memo>"
				: "<memo>" + memo + "</memo>";
		content.add(BetaContentBlockParam.ofText(text));
		return calls.base().system(LabelPrompt.SYSTEM).addUserMessageOfContentBlockParams(content);
	}

	static LabelResult toResult(Reply r) {
		List<PhotoBox> boxes = r.boxes() == null ? List.of() : r.boxes().stream()
				.map(b -> new PhotoBox(b.index(), new FocusBox(b.x(), b.y(), b.w(), b.h())))
				.toList();
		return new LabelResult(r.drinkName(), r.category(), decimal(r.abv()), r.volumeMl(), r.breweryName(),
				decimal(r.rating()), r.tasteNotes(), r.pairing(), boxes);
	}

	private static BigDecimal decimal(Double d) {
		return d == null ? null : BigDecimal.valueOf(d);
	}

	record Reply(
			@JsonPropertyDescription("술 이름. 모르면 null") String drinkName,
			@JsonPropertyDescription("종류(탁주, 약주, 청주, 증류주, 과실주 등). 모르면 null") String category,
			@JsonPropertyDescription("도수(%). 라벨에 보일 때만") Double abv,
			@JsonPropertyDescription("용량(ml). 라벨에 보일 때만") Integer volumeMl,
			@JsonPropertyDescription("양조장 이름. 라벨에 보일 때만") String breweryName,
			@JsonPropertyDescription("별점 0~5. 메모에 숫자나 별로 적힌 경우만") Double rating,
			@JsonPropertyDescription("맛 표현. 메모의 표현 그대로") String tasteNotes,
			@JsonPropertyDescription("어울리는 음식. 메모의 표현 그대로") String pairing,
			@JsonPropertyDescription("사진별 병 상자. 병이 없는 사진은 생략") List<Box> boxes) {
	}

	record Box(
			@JsonPropertyDescription("입력 사진 순서(0부터)") int index,
			@JsonPropertyDescription("왼쪽 가장자리, 원본 대비 0~1") double x,
			@JsonPropertyDescription("위쪽 가장자리, 원본 대비 0~1") double y,
			@JsonPropertyDescription("너비, 원본 대비 0~1") double w,
			@JsonPropertyDescription("높이, 원본 대비 0~1") double h) {
	}
}
