package com.kdrinker.caption;

import com.kdrinker.brewery.entity.Brewery;
import java.util.ArrayList;
import java.util.List;

/** 저장된 캡션을 양조장 블록 앞(훅·소개·맛·별점)과 뒤(해시태그)로 나눈 것. */
public record CaptionParts(String head, String tail) {

	public static CaptionParts split(String stored) {
		if (stored == null) {
			return new CaptionParts("", "");
		}
		int first = stored.indexOf(BreweryBlock.MARKER);
		if (first < 0) {
			return new CaptionParts(stored.strip(), "");
		}
		int last = stored.lastIndexOf(BreweryBlock.MARKER);
		return new CaptionParts(stored.substring(0, first).strip(),
				stored.substring(last + BreweryBlock.MARKER.length()).strip());
	}

	/** 머리, 양조장 블록, 꼬리를 빈 줄로 잇는다. 빈 조각은 뺀다. 블록은 항상 넣는다. */
	public String join(Brewery brewery) {
		List<String> blocks = new ArrayList<>();
		if (!head.isEmpty()) {
			blocks.add(head);
		}
		blocks.add(BreweryBlock.render(brewery));
		if (!tail.isEmpty()) {
			blocks.add(tail);
		}
		return String.join("\n\n", blocks);
	}
}
