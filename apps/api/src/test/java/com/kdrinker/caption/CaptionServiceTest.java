package com.kdrinker.caption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.analysis.claude.ClaudeUnavailableException;
import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptionServiceTest {

	private final CaptionWriter writer = mock(CaptionWriter.class);
	private final CaptionService service = new CaptionService(writer);
	private final Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);
	private final LabelResult label = new LabelResult("화요25", null, null, null, null, null, null, null, List.of());
	private final CaptionInput in = new CaptionInput(label, new BigDecimal("4.5"), brewery, "메모");

	private final CaptionValues values = CaptionValues.of(label, brewery, new BigDecimal("4.5"));

	private static final List<DraftTag> TAGS = List.of(new DraftTag("#화요25", TagCategory.NAME),
			new DraftTag("#여주", TagCategory.REGION), new DraftTag("#화요", TagCategory.BREWERY),
			new DraftTag("#전과막걸리", TagCategory.PAIRING_TASTE), new DraftTag("#달달", TagCategory.PAIRING_TASTE));

	/** "달아요. " 반복 문장 n개. 한 문장은 5자(공백 포함 5자는 마지막 제외). */
	private static String sentences(int n) {
		return "달아요. ".repeat(n).strip();
	}

	private static CaptionDraft draft(String tasting) {
		return new CaptionDraft("훅", "소개", tasting, TAGS);
	}

	@Test
	void 상한_이하면_shorten을_부르지_않고_그대로_쓴다() {
		// given
		when(writer.write(in)).thenReturn(draft("달아요."));

		// when
		ComposedCaption out = service.compose(in);

		// then
		verify(writer, never()).shorten(any(), any(), anyInt());
		assertThat(out.caption()).contains(BreweryBlock.TEMPLATE).contains("{별점}").contains("달아요.");
		assertThat(out.hashtags()).startsWith("#전통주 #우리술").contains("#{술이름태그}").contains("#달달");
		assertThat(out.caption()).endsWith(out.hashtags());
	}

	@Test
	void 초과하면_shorten을_한_번_부르고_상한_이하면_그_결과를_쓴다() {
		// given
		when(writer.write(in)).thenReturn(draft(sentences(500)));
		when(writer.shorten(any(), any(), anyInt())).thenReturn(draft("짧아요."));

		// when
		ComposedCaption out = service.compose(in);

		// then
		verify(writer, times(1)).shorten(any(), any(), anyInt());
		assertThat(out.caption()).contains("짧아요.").doesNotContain("달아요.");
		assertThat(CaptionAssembler.length(out.caption(), values)).isLessThanOrEqualTo(CaptionService.MAX_LENGTH);
	}

	@Test
	void shorten에_현재_글자_수가_넘어간다() {
		// given
		CaptionDraft first = draft(sentences(500));
		when(writer.write(in)).thenReturn(first);
		when(writer.shorten(any(), any(), anyInt())).thenReturn(draft("짧아요."));
		String stored = CaptionAssembler.assemble(first,
				Hashtags.finalize(TAGS, label, brewery).joined());

		// when
		service.compose(in);

		// then
		verify(writer).shorten(in, first, CaptionAssembler.length(stored, values));
	}

	@Test
	void shorten도_초과하면_태그가_먼저_줄고_그래도_넘으면_tasting이_뒤_문장부터_빠진다() {
		// given
		String longTasting = sentences(460); // 약 2,300자
		when(writer.write(in)).thenReturn(draft(longTasting));
		when(writer.shorten(any(), any(), anyInt())).thenReturn(draft(longTasting));

		// when
		ComposedCaption out = service.compose(in);

		// then
		assertThat(CaptionAssembler.length(out.caption(), values)).isLessThanOrEqualTo(CaptionService.MAX_LENGTH);
		assertThat(out.hashtags()).doesNotContain("#달달").doesNotContain("#전과막걸리").doesNotContain("#여주")
				.doesNotContain("#{양조장태그}").contains("#{술이름태그}");
		String shown = CaptionAssembler.render(out.caption(), values);
		String tasting = shown.split("\n\n")[2];
		assertThat(tasting).endsWith("달아요.").matches("(달아요\\. )*달아요\\.");
		assertThat(tasting.length()).isLessThan(longTasting.length());
	}

	@Test
	void 태그를_줄이는_것만으로_충분하면_tasting은_그대로다() {
		// given: 전체 태그로는 처음 상한을 넘는 문장 수를 찾는다(태그 몇 개만 빼면 들어가는 크기)
		HashtagSet full = Hashtags.finalize(TAGS, label, brewery);
		int n = 1;
		while (CaptionAssembler.length(CaptionAssembler.assemble(draft(sentences(n)), full.joined()), values) <= CaptionService.MAX_LENGTH) {
			n++;
		}
		String tasting = sentences(n);
		when(writer.write(in)).thenReturn(draft(tasting));
		when(writer.shorten(any(), any(), anyInt())).thenReturn(draft(tasting));

		// when
		ComposedCaption out = service.compose(in);

		// then
		assertThat(CaptionAssembler.length(out.caption(), values)).isLessThanOrEqualTo(CaptionService.MAX_LENGTH);
		assertThat(out.caption()).contains("\n\n" + tasting + "\n\n");
		assertThat(out.hashtags().split(" ")).hasSizeLessThan(full.all().size());
	}

	@Test
	void shorten이_실패해도_코드_폴백으로_상한_이하가_된다() {
		// given
		when(writer.write(in)).thenReturn(draft(sentences(460)));
		when(writer.shorten(any(), any(), anyInt())).thenThrow(new ClaudeUnavailableException("실패"));

		// when
		ComposedCaption out = service.compose(in);

		// then
		assertThat(CaptionAssembler.length(out.caption(), values)).isLessThanOrEqualTo(CaptionService.MAX_LENGTH);
	}

	@Test
	void 문장_하나짜리_tasting이_넘치면_통째로_빠지고_빈_블록과_빈_줄이_남지_않는다() {
		// given
		String oneLong = "달아요".repeat(800) + ".";
		when(writer.write(in)).thenReturn(draft(oneLong));
		when(writer.shorten(any(), any(), anyInt())).thenReturn(draft(oneLong));

		// when
		ComposedCaption out = service.compose(in);

		// then
		String shown = CaptionAssembler.render(out.caption(), values);
		assertThat(CaptionAssembler.length(out.caption(), values)).isLessThanOrEqualTo(CaptionService.MAX_LENGTH);
		assertThat(shown).doesNotContain("달아요").doesNotContain("\n\n\n");
	}

	@Test
	void compose_lengthCheckUsesFilledLength() {
		// given: 이름표 글자({술이름} 4자)로는 상한을 넘지만 채운 글("가")로는 넘지 않는다
		String tasting = "{술이름}".repeat(500);
		LabelResult one = new LabelResult("가", null, null, null, null, null, null, null, List.of());
		CaptionInput input = new CaptionInput(one, null, null, "메모");
		when(writer.write(input)).thenReturn(new CaptionDraft("훅", "소개", tasting, List.of()));

		// when
		ComposedCaption out = service.compose(input);

		// then
		assertThat(out.caption().length()).isGreaterThan(CaptionService.MAX_LENGTH);
		verify(writer, never()).shorten(any(), any(), anyInt());
		assertThat(out.caption()).contains(tasting);
	}

	@Test
	void compose_stripsUnknownPlaceholdersFromClaude() {
		// given
		when(writer.write(in)).thenReturn(new CaptionDraft("훅 {메뉴}", "{술이름}은(는) 소개", "{메뉴}랑 먹어요.", TAGS));

		// when
		ComposedCaption out = service.compose(in);

		// then
		assertThat(out.caption()).doesNotContain("{메뉴}").contains("{술이름}은(는) 소개").contains("랑 먹어요.");
	}

	@Test
	void 문장_중간에서_끊지_않는다() {
		assertThat(CaptionService.dropLastSentence("달아요. 시원해요 좋아요. 끝")).isEqualTo("달아요. 시원해요 좋아요.");
		assertThat(CaptionService.dropLastSentence("달아요. 시원해요")).isEqualTo("달아요.");
		assertThat(CaptionService.dropLastSentence("달아요")).isEmpty();
		assertThat(CaptionService.dropLastSentence("가요요 나")).isEqualTo("가요요");
	}
}
