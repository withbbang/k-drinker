package com.kdrinker.brewery.service;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BreweryAlias;
import com.kdrinker.brewery.repository.BreweryAliasRepository;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.common.Names;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이름·별칭 정확 일치로만 찾는다. 후보가 둘 이상이면 모호하므로 비어 있다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BreweryLookup {

	private final BreweryRepository breweryRepository;
	private final BreweryAliasRepository breweryAliasRepository;
	private final DrinkRepository drinkRepository;

	public Optional<Brewery> findByName(String raw) {
		String key = Names.normalize(raw);
		if (key.isEmpty()) {
			return Optional.empty();
		}
		List<Brewery> byName = breweryRepository.findByNormalizedName(key);
		if (!byName.isEmpty()) {
			return single(byName);
		}
		List<Brewery> byAlias = breweryAliasRepository.findByNormalizedAlias(key).stream()
				.map(BreweryAlias::getBrewery).distinct().toList();
		return single(byAlias);
	}

	public Optional<Drink> findDrink(String raw) {
		String key = Names.normalize(raw);
		if (key.isEmpty()) {
			return Optional.empty();
		}
		List<Drink> byName = drinkRepository.findByNormalizedName(key);
		if (!byName.isEmpty()) {
			return single(byName);
		}
		List<Drink> byAlias = drinkRepository.findByAliasesIsNotNull().stream()
				.filter(d -> Arrays.stream(d.getAliases().split(","))
						.anyMatch(a -> key.equals(Names.normalize(a))))
				.toList();
		return single(byAlias);
	}

	private static <T> Optional<T> single(List<T> list) {
		return list.size() == 1 ? Optional.of(list.get(0)) : Optional.empty();
	}

}
