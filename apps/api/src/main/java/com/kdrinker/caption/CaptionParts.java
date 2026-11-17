package com.kdrinker.caption;

import com.kdrinker.brewery.entity.Brewery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

	/**
	 * 머리 끝의 별점 블록을 새 값으로 바꾼다. 새 값이 null이면 블록을 뺀다.
	 * 이전 별점이 있는데 머리 끝이 그 블록이 아니면(직접 고친 문장) 그대로 둔다.
	 */
	public CaptionParts withRating(BigDecimal before, BigDecimal after) {
		String oldBlock = before == null ? null : CaptionAssembler.ratingBlock(before);
		String newBlock = after == null ? null : CaptionAssembler.ratingBlock(after);
		if (Objects.equals(oldBlock, newBlock)) {
			return this;
		}
		String rest = head;
		if (oldBlock != null) {
			if (head.equals(oldBlock)) {
				rest = "";
			} else if (head.endsWith("\n\n" + oldBlock)) {
				rest = head.substring(0, head.length() - oldBlock.length() - 2);
			} else {
				return this;
			}
		}
		if (newBlock != null) {
			rest = rest.isEmpty() ? newBlock : rest + "\n\n" + newBlock;
		}
		return new CaptionParts(rest, tail);
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
