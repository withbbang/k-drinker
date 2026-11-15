package com.kdrinker.caption;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 구성표 순서로 분류된 해시태그. 분류를 유지해 삭제 우선순위를 적용할 수 있다. */
public record HashtagSet(List<String> fixed, String type, String name, String region, String brewery,
		List<String> pairingTaste) {

	/** 구성표 순서(고정, 종류, 이름, 지역, 양조장, 페어링·맛)로 편 목록. */
	public List<String> all() {
		List<String> all = new ArrayList<>(fixed);
		for (String t : new String[] {type, name, region, brewery}) {
			if (t != null) {
				all.add(t);
			}
		}
		all.addAll(pairingTaste);
		return List.copyOf(all);
	}

	/** 삭제 우선순위(페어링·맛 뒤에서부터 → 지역 → 양조장)로 한 개 뺀다. 고정·종류·이름만 남았으면 empty. */
	public Optional<HashtagSet> dropOne() {
		if (!pairingTaste.isEmpty()) {
			return Optional.of(new HashtagSet(fixed, type, name, region, brewery,
					pairingTaste.subList(0, pairingTaste.size() - 1)));
		}
		if (region != null) {
			return Optional.of(new HashtagSet(fixed, type, name, null, brewery, pairingTaste));
		}
		if (brewery != null) {
			return Optional.of(new HashtagSet(fixed, type, name, region, null, pairingTaste));
		}
		return Optional.empty();
	}

	/** 공백으로 구분한 한 덩어리. */
	public String joined() {
		return String.join(" ", all());
	}
}
