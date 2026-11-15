package com.kdrinker.caption;

import com.kdrinker.brewery.entity.Brewery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 블록 조립. 순서: 훅, 소개, 맛·페어링, 별점, 양조장 블록, 해시태그. 블록 사이는 빈 줄 하나. */
public final class CaptionAssembler {

	private CaptionAssembler() {
	}

	/** 마커를 포함한 저장용 캡션. 입력에 없는 블록은 통째로 뺀다(양조장 블록만 마커 두 줄로 남긴다). */
	public static String assemble(CaptionDraft d, BigDecimal rating, Brewery b, String hashtags) {
		List<String> blocks = new ArrayList<>();
		addIfPresent(blocks, d.hook());
		addIfPresent(blocks, d.intro());
		addIfPresent(blocks, d.tasting());
		if (rating != null) {
			blocks.add("★" + rating.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString());
		}
		blocks.add(BreweryBlock.render(b));
		addIfPresent(blocks, hashtags);
		return String.join("\n\n", blocks);
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

	/** 마커를 뺀 코드 포인트 수. 이모지는 한 글자로 센다. */
	public static int length(String stored) {
		String s = stripMarkers(stored);
		return s.codePointCount(0, s.length());
	}
}
