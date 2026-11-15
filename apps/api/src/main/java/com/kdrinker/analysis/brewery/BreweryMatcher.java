package com.kdrinker.analysis.brewery;

import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.brewery.service.BreweryLookup;
import com.kdrinker.common.Names;
import com.kdrinker.drink.entity.Drink;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 라벨에서 읽은 이름으로 양조장을 정한다. ① 술 이름으로 찾은 술의 양조장 ② 양조장 이름·별칭 ③ 웹 조사.
 * ③에서 찾은 것은 미검증(verified=false) WEB_SEARCH 양조장으로 저장한다. 같은 정규화 이름이 이미 있으면 그것(가장 작은 id)을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class BreweryMatcher {

	private final BreweryLookup lookup;
	private final BreweryRepository breweryRepository;
	private final BreweryResearcher researcher;

	/** 조사(외부 호출)도 이 트랜잭션 안에서 한다. 분석은 한 번에 하나씩 돌기 때문에 받아들인다. */
	@Transactional
	public Optional<Brewery> match(LabelResult label) {
		if (label == null) {
			return Optional.empty();
		}
		Optional<Brewery> byDrink = lookup.findDrink(label.drinkName())
				.map(Drink::getBrewery)
				.map(brewery -> Hibernate.unproxy(brewery, Brewery.class));
		if (byDrink.isPresent()) {
			return byDrink;
		}
		Optional<Brewery> byName = lookup.findByName(label.breweryName());
		if (byName.isPresent()) {
			return byName;
		}
		if (Names.normalize(label.breweryName()).isEmpty() && Names.normalize(label.drinkName()).isEmpty()) {
			return Optional.empty();
		}
		return researcher.research(label.breweryName(), label.drinkName()).flatMap(this::saveOrReuse);
	}

	private Optional<Brewery> saveOrReuse(ResearchedBrewery found) {
		String key = Names.normalize(found.name());
		if (key.isEmpty()) {
			return Optional.empty();
		}
		List<Brewery> existing = breweryRepository.findByNormalizedName(key);
		if (!existing.isEmpty()) {
			return existing.stream().min(Comparator.comparing(Brewery::getId));
		}
		Brewery brewery = Brewery.of(found.name().strip(), key, BrewerySource.WEB_SEARCH);
		brewery.updateContact(found.address(), found.phone(), found.homepage(), handle(found.instagramHandle()),
				null, found.sourceUrl());
		return Optional.of(breweryRepository.save(brewery));
	}

	private static String handle(String raw) {
		if (raw == null) {
			return null;
		}
		String h = raw.strip();
		if (h.startsWith("@")) {
			h = h.substring(1).strip();
		}
		return h.isEmpty() ? null : h;
	}
}
