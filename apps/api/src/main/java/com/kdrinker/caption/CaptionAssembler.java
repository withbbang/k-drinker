package com.kdrinker.caption;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 블록 조립. 순서: 훅, 소개, 맛·페어링, 별점 이름표, 양조장 블록 이름표, 해시태그. 블록 사이는 빈 줄 하나. */
public final class CaptionAssembler {

	private CaptionAssembler() {
	}

	/** 마커를 포함한 저장용 캡션 틀. 별점·양조장 블록은 항상 이름표로 두고, 채울 때 값이 없으면 빠진다. */
	public static String assemble(CaptionDraft d, String hashtags) {
		List<String> blocks = new ArrayList<>();
		addIfPresent(blocks, d.hook());
		addIfPresent(blocks, d.intro());
		addIfPresent(blocks, d.tasting());
		blocks.add("{별점}");
		blocks.add(BreweryBlock.TEMPLATE);
		addIfPresent(blocks, hashtags);
		return String.join("\n\n", blocks);
	}

	/** 별점 블록. 소수 한 자리로 맞춘다(예: ★4.0). */
	public static String ratingBlock(BigDecimal rating) {
		return "★" + rating.setScale(1, RoundingMode.HALF_UP).toPlainString();
	}

	private static void addIfPresent(List<String> blocks, String s) {
		if (s != null && !s.isBlank()) {
			blocks.add(s.strip());
		}
	}

	/** 게시·표시용: 마커를 지우고, 빈 양조장 블록이 남기는 빈 줄은 하나로 접는다. */
	public static String stripMarkers(String stored) {
		return stored.replaceAll(BreweryBlock.MARKER + "\n?", "").replaceAll("\n{3,}", "\n\n").strip();
	}

	/** 게시·표시용: 이름표를 채운 뒤 마커를 지운다. */
	public static String render(String stored, CaptionValues v) {
		return stripMarkers(Placeholders.fill(stored, v));
	}

	/** 채운 뒤 마커를 뺀 코드 포인트 수. 이모지는 한 글자로 센다. */
	public static int length(String stored, CaptionValues v) {
		String s = render(stored, v);
		return s.codePointCount(0, s.length());
	}
}
