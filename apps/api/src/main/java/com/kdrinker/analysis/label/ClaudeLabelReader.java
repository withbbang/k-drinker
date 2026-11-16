package com.kdrinker.analysis.label;

import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kdrinker.analysis.claude.ClaudeCalls;
import com.kdrinker.analysis.claude.PromptText;
import com.kdrinker.media.FocusBox;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
				: "<memo>" + PromptText.neutral(memo) + "</memo>";
		content.add(BetaContentBlockParam.ofText(text));
		return calls.base().system(LabelPrompt.SYSTEM).addUserMessageOfContentBlockParams(content);
	}

	static LabelResult toResult(Reply r) {
		List<PhotoBox> boxes = r.boxes() == null ? List.of() : r.boxes().stream()
				.map(b -> new PhotoBox(b.index(), new FocusBox(b.x(), b.y(), b.w(), b.h())))
				.toList();
		return new LabelResult(value(r.drinkName()), value(r.category()), decimal(r.abv()), value(r.volumeMl()),
				value(r.breweryName()), decimal(r.rating()), value(r.tasteNotes()), value(r.pairing()), boxes);
	}

	private static <T> T value(Optional<T> o) {
		return o == null ? null : o.orElse(null);
	}

	private static BigDecimal decimal(Optional<Double> d) {
		Double v = value(d);
		return v == null ? null : BigDecimal.valueOf(v);
	}

	/**
	 * 모르는 값은 null로 답할 수 있어야 한다. SDK 스키마 생성기는 박싱 타입도 필수·non-null({@code "type":"number"})로
	 * 만들고 {@code Optional}만 {@code ["number","null"]}로 만든다. 그래서 선택 값은 모두 Optional로 둔다.
	 */
	record Reply(
			@JsonPropertyDescription("술 이름. 모르면 null") Optional<String> drinkName,
			@JsonPropertyDescription("종류(탁주, 약주, 청주, 증류주, 과실주 등). 모르면 null") Optional<String> category,
			@JsonPropertyDescription("도수(%). 라벨에 보일 때만, 아니면 null") Optional<Double> abv,
			@JsonPropertyDescription("용량(ml). 라벨에 보일 때만, 아니면 null") Optional<Integer> volumeMl,
			@JsonPropertyDescription("양조장 이름. 라벨에 보일 때만, 아니면 null") Optional<String> breweryName,
			@JsonPropertyDescription("별점 0~5. 메모에 숫자나 별로 적힌 경우만, 아니면 null") Optional<Double> rating,
			@JsonPropertyDescription("맛 표현. 메모의 표현 그대로, 없으면 null") Optional<String> tasteNotes,
			@JsonPropertyDescription("어울리는 음식. 메모의 표현 그대로, 없으면 null") Optional<String> pairing,
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
