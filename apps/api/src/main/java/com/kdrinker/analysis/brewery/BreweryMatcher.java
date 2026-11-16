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
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 라벨에서 읽은 이름으로 양조장을 정한다. ① 술 이름으로 찾은 술의 양조장 ② 양조장 이름·별칭 ③ 웹 조사.
 * ③에서 찾은 것은 미검증(verified=false) WEB_SEARCH 양조장으로 저장한다. 같은 정규화 이름이 이미 있으면 그것(가장 작은 id)을 쓴다.
 * <p>
 * 트랜잭션은 짧게 나눈다: ①② 조회는 읽기 전용 트랜잭션, ③ 조사(외부 호출, 수십 초)는 트랜잭션 밖, 저장은 별도 트랜잭션.
 * 조사 동안 DB 연결을 잡고 있지 않기 위해서다. 돌려주는 Brewery는 프록시를 벗긴 것이라 트랜잭션 밖에서 읽어도 된다.
 */
@Service
public class BreweryMatcher {

	private final BreweryLookup lookup;
	private final BreweryRepository breweryRepository;
	private final BreweryResearcher researcher;
	private final TransactionTemplate readTx;
	private final TransactionTemplate writeTx;

	public BreweryMatcher(BreweryLookup lookup, BreweryRepository breweryRepository, BreweryResearcher researcher,
			PlatformTransactionManager transactionManager) {
		this.lookup = lookup;
		this.breweryRepository = breweryRepository;
		this.researcher = researcher;
		this.readTx = new TransactionTemplate(transactionManager);
		this.readTx.setReadOnly(true);
		this.writeTx = new TransactionTemplate(transactionManager);
	}

	public Optional<Brewery> match(LabelResult label) {
		if (label == null) {
			return Optional.empty();
		}
		Optional<Brewery> known = readTx.execute(status -> findKnown(label));
		if (known != null && known.isPresent()) {
			return known;
		}
		if (Names.normalize(label.breweryName()).isEmpty() && Names.normalize(label.drinkName()).isEmpty()) {
			return Optional.empty();
		}
		Optional<ResearchedBrewery> found = researcher.research(label.breweryName(), label.drinkName())
				.map(ResearchedBrewery::sanitized);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Optional<Brewery> saved = writeTx.execute(status -> saveOrReuse(found.get()));
		return saved == null ? Optional.empty() : saved;
	}

	private Optional<Brewery> findKnown(LabelResult label) {
		Optional<Brewery> byDrink = lookup.findDrink(label.drinkName())
				.map(Drink::getBrewery)
				.map(brewery -> Hibernate.unproxy(brewery, Brewery.class));
		if (byDrink.isPresent()) {
			return byDrink;
		}
		return lookup.findByName(label.breweryName()).map(brewery -> Hibernate.unproxy(brewery, Brewery.class));
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
		brewery.updateContact(found.address(), found.phone(), found.homepage(), found.instagramHandle(), null,
				found.sourceUrl());
		return Optional.of(breweryRepository.save(brewery));
	}
}
