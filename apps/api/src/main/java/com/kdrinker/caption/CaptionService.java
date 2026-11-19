package com.kdrinker.caption;

import com.kdrinker.analysis.claude.ClaudeUnavailableException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 캡션을 만들고 2,200자 상한에 맞춘다. 글자 수는 항상 코드로 센다. */
@Service
public class CaptionService {

	public static final int MAX_LENGTH = 2200;

	/** 문장 경계: `.` 또는 `요` 뒤의 공백. */
	private static final Pattern BOUNDARY = Pattern.compile("(?<=[.요])\\s+");

	private final CaptionWriter writer;

	public CaptionService(CaptionWriter writer) {
		this.writer = writer;
	}

	public ComposedCaption compose(CaptionInput in) {
		CaptionValues values = CaptionValues.of(in.label(), in.brewery(), in.rating());
		CaptionDraft draft = clean(writer.write(in));
		HashtagSet tags = Hashtags.finalize(draft.hashtags(), in.label(), in.brewery());
		String caption = build(draft, tags);
		if (CaptionAssembler.length(caption, values) <= MAX_LENGTH) {
			return result(caption, tags);
		}
		try {
			CaptionDraft shorter = clean(writer.shorten(in, draft, CaptionAssembler.length(caption, values)));
			draft = shorter;
			tags = Hashtags.finalize(shorter.hashtags(), in.label(), in.brewery());
			caption = build(draft, tags);
		} catch (ClaudeUnavailableException e) {
			// 줄이기에 실패해도 코드의 폴백(태그 삭제, 문장 제거)으로 계속한다
		}
		while (CaptionAssembler.length(caption, values) > MAX_LENGTH) {
			Optional<HashtagSet> fewer = tags.dropOne();
			if (fewer.isEmpty()) {
				break;
			}
			tags = fewer.get();
			caption = build(draft, tags);
		}
		while (CaptionAssembler.length(caption, values) > MAX_LENGTH && hasText(draft.tasting())) {
			draft = new CaptionDraft(draft.hook(), draft.intro(), dropLastSentence(draft.tasting()), draft.hashtags());
			caption = build(draft, tags);
		}
		return result(caption, tags);
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	/** Claude가 쓴 글에서 목록에 없는 중괄호를 지운다. */
	private static CaptionDraft clean(CaptionDraft d) {
		return new CaptionDraft(Placeholders.stripUnknown(d.hook()), Placeholders.stripUnknown(d.intro()),
				Placeholders.stripUnknown(d.tasting()), d.hashtags());
	}

	private static String build(CaptionDraft draft, HashtagSet tags) {
		return CaptionAssembler.assemble(draft, tags.joined());
	}

	private static ComposedCaption result(String caption, HashtagSet tags) {
		return new ComposedCaption(caption, tags.joined());
	}

	/** 마지막 문장 하나를 뺀다. 경계가 없으면 한 문장뿐이므로 전부 뺀다. 문장 중간은 자르지 않는다. */
	static String dropLastSentence(String tasting) {
		String t = tasting.strip();
		int cut = -1;
		Matcher m = BOUNDARY.matcher(t);
		while (m.find()) {
			cut = m.start();
		}
		return cut < 0 ? "" : t.substring(0, cut);
	}
}
