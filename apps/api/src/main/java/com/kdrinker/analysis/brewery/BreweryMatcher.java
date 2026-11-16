package com.kdrinker.analysis.brewery;

import com.kdrinker.analysis.claude.ClaudeUnavailableException;
import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.brewery.service.BreweryLookup;
import com.kdrinker.common.Names;
import com.kdrinker.drink.entity.Drink;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 라벨에서 읽은 이름으로 양조장을 정한다. ① 술 이름으로 찾은 술의 양조장 ② 양조장 이름·별칭(같은 이름이 여럿이면 가장
 * 작은 id) ③ 웹 조사.
 * ③에서 찾은 것은 미검증(verified=false) WEB_SEARCH 양조장으로 저장한다. 같은 정규화 이름이 이미 있으면 그것(가장 작은 id)을 쓴다.
 * <p>
 * 트랜잭션은 짧게 나눈다: ①② 조회는 읽기 전용 트랜잭션, ③ 조사(외부 호출, 수십 초)는 트랜잭션 밖, 저장은 별도 트랜잭션.
 * 조사 동안 DB 연결을 잡고 있지 않기 위해서다. 돌려주는 Brewery는 프록시를 벗긴 것이라 트랜잭션 밖에서 읽어도 된다.
 * <p>
 * 빈 칸 보강: ①②로 찾은 양조장의 주소·전화·홈페이지·인스타그램 중 빈 칸이 있고 아직 조사하지 않았으면(researched_at NULL)
 * 한 번 웹 조사해 빈 칸만 채운다. 조사한 이름이 그 양조장과 같을 때만 받고, 값이 있는 칸·출처·검증 여부는 바꾸지 않는다.
 * 조사가 끝나면(찾았든 못 찾았든) researched_at을 남긴다. 보강 조사의 Claude 오류는 게시물을 실패시키지 않는다.
 */
@Slf4j
@Service
public class BreweryMatcher {

	private final BreweryLookup lookup;
	private final BreweryRepository breweryRepository;
	private final BreweryResearcher researcher;
	private final Clock clock;
	private final TransactionTemplate readTx;
	private final TransactionTemplate writeTx;

	public BreweryMatcher(BreweryLookup lookup, BreweryRepository breweryRepository, BreweryResearcher researcher,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.lookup = lookup;
		this.breweryRepository = breweryRepository;
		this.researcher = researcher;
		this.clock = clock;
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
			return Optional.of(fillBlanks(known.get(), label));
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

	/** 빈 칸이 있고 아직 조사하지 않은 양조장만 한 번 조사해 빈 칸을 채운다. 그 밖에는 받은 그대로 돌려준다. */
	private Brewery fillBlanks(Brewery known, LabelResult label) {
		if (known.getResearchedAt() != null || !known.hasBlankContact()) {
			return known;
		}
		Optional<ResearchedBrewery> found;
		try {
			found = researcher.research(known.getName(), label.drinkName()).map(ResearchedBrewery::sanitized);
		} catch (ClaudeUnavailableException e) {
			log.warn("양조장 빈 칸 보강 조사를 건너뜁니다. breweryId={} cause={}", known.getId(), e.toString());
			return known;
		}
		Brewery filled = writeTx.execute(status -> recordResearch(known.getId(), found.orElse(null)));
		return filled == null ? known : filled;
	}

	/** 보강 결과를 반영하고 조사 시각을 남긴다. 그 사이 다른 쪽이 먼저 조사했으면 아무것도 바꾸지 않는다. */
	private Brewery recordResearch(Long breweryId, ResearchedBrewery found) {
		Brewery brewery = breweryRepository.findById(breweryId).orElse(null);
		if (brewery == null) {
			return null;
		}
		if (brewery.getResearchedAt() == null) {
			if (found == null) {
				log.info("양조장 빈 칸 보강 breweryId={} outcome=no result", breweryId);
			} else if (!sameBrewery(brewery, found)) {
				log.info("양조장 빈 칸 보강 breweryId={} outcome=name mismatch researched={} db={}", breweryId,
						found.name(), brewery.getName());
			} else {
				fill(brewery, found, "blank-fill");
			}
			brewery.markResearched(clock.instant());
		}
		return Hibernate.unproxy(brewery, Brewery.class);
	}

	/** 빈 칸만 채우고(값이 있는 칸·출처·검증 여부는 그대로) 채운 칸을 로그에 남긴다. 값은 남기지 않는다. */
	private static void fill(Brewery brewery, ResearchedBrewery found, String path) {
		List<String> filled = brewery.fillBlankContact(found.address(), found.phone(), found.homepage(),
				found.instagramHandle());
		log.info("양조장 빈 칸 보강 breweryId={} path={} outcome=filled filled={}", brewery.getId(), path, filled);
	}

	/** 조사한 이름이 그 양조장의 정규화 이름이거나, 이름·별칭으로 찾았을 때 그 양조장이면 같은 양조장이다. */
	private boolean sameBrewery(Brewery brewery, ResearchedBrewery found) {
		String key = Names.normalize(found.name());
		if (key.isEmpty()) {
			return false;
		}
		return key.equals(brewery.getNormalizedName())
				|| lookup.findByName(found.name()).map(b -> b.getId().equals(brewery.getId())).orElse(false);
	}

	private Optional<Brewery> findKnown(LabelResult label) {
		Optional<Brewery> byDrink = lookup.findDrink(label.drinkName())
				.map(Drink::getBrewery)
				.map(brewery -> Hibernate.unproxy(brewery, Brewery.class));
		if (byDrink.isPresent()) {
			return byDrink;
		}
		Optional<Brewery> byName = lookup.findByName(label.breweryName());
		if (byName.isEmpty()) {
			byName = lowestIdOfSameName(label.breweryName());
		}
		return byName.map(brewery -> Hibernate.unproxy(brewery, Brewery.class));
	}

	/**
	 * 정규화 이름이 같은 양조장이 여럿이면(조회에서는 모호해 비어 있음) 가장 작은 id를 쓴다. 조사해도 결국 같은 규칙으로
	 * 그중 하나를 고르므로 유료 웹 조사를 건너뛴다. 별칭만 겹치는 경우는 서로 다른 양조장일 수 있어 그대로 둔다.
	 */
	private Optional<Brewery> lowestIdOfSameName(String breweryName) {
		String key = Names.normalize(breweryName);
		if (key.isEmpty()) {
			return Optional.empty();
		}
		return breweryRepository.findByNormalizedName(key).stream().min(Comparator.comparing(Brewery::getId));
	}

	private Optional<Brewery> saveOrReuse(ResearchedBrewery found) {
		String key = Names.normalize(found.name());
		if (key.isEmpty()) {
			return Optional.empty();
		}
		List<Brewery> existing = breweryRepository.findByNormalizedName(key);
		if (!existing.isEmpty()) {
			// 조사 결과 이름으로 기존 양조장을 다시 쓴다. 이미 낸 조사 비용을 버리지 않게 빈 칸을 채우고 조사 시각을 남긴다.
			Brewery reused = existing.stream().min(Comparator.comparing(Brewery::getId)).orElseThrow();
			fill(reused, found, "reuse");
			if (reused.getResearchedAt() == null) {
				reused.markResearched(clock.instant());
			}
			return Optional.of(Hibernate.unproxy(reused, Brewery.class));
		}
		Brewery brewery = Brewery.of(found.name().strip(), key, BrewerySource.WEB_SEARCH);
		brewery.updateContact(found.address(), found.phone(), found.homepage(), found.instagramHandle(), null,
				found.sourceUrl());
		brewery.markResearched(clock.instant());
		return Optional.of(breweryRepository.save(brewery));
	}
}
