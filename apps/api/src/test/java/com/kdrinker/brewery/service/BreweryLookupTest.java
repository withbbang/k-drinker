package com.kdrinker.brewery.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BreweryAlias;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryAliasRepository;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(BreweryLookup.class)
class BreweryLookupTest {

	@Autowired
	private BreweryLookup lookup;

	@Autowired
	private BreweryRepository breweryRepository;

	@Autowired
	private BreweryAliasRepository aliasRepository;

	@Autowired
	private DrinkRepository drinkRepository;

	private Brewery hwayo;

	@BeforeEach
	void setUp() {
		hwayo = breweryRepository.save(Brewery.of("(주)화요", "화요", BrewerySource.PUBLIC_DATA));
	}

	@Test
	void 정규화한_이름으로_양조장을_찾는다() {
		// when
		var found = lookup.findByName("화요 ");

		// then
		assertThat(found).contains(hwayo);
	}

	@Test
	void 별칭으로_양조장을_찾는다() {
		// given
		aliasRepository.save(BreweryAlias.of(hwayo, "hwayo"));

		// when
		var found = lookup.findByName("HWAYO");

		// then
		assertThat(found).contains(hwayo);
	}

	@Test
	void 이름이_같은_양조장이_둘이면_비어_있다() {
		// given
		breweryRepository.save(Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA));

		// when / then
		assertThat(lookup.findByName("화요")).isEmpty();
	}

	@Test
	void 별칭이_여러_양조장에_걸리면_비어_있다() {
		// given
		Brewery other = breweryRepository.save(Brewery.of("다른술", "다른술", BrewerySource.PUBLIC_DATA));
		aliasRepository.save(BreweryAlias.of(hwayo, "공통"));
		aliasRepository.save(BreweryAlias.of(other, "공통"));

		// when / then
		assertThat(lookup.findByName("공통")).isEmpty();
	}

	@Test
	void 없는_이름과_빈_값은_비어_있다() {
		assertThat(lookup.findByName("없는양조장")).isEmpty();
		assertThat(lookup.findByName(null)).isEmpty();
		assertThat(lookup.findByName("  ")).isEmpty();
	}

	@Test
	void 부분_일치는_하지_않는다() {
		assertThat(lookup.findByName("화")).isEmpty();
	}

	@Test
	void 술_이름으로_찾는다() {
		// given
		Drink drink = drinkRepository.save(Drink.of("화요25", "화요25", hwayo));

		// when
		var found = lookup.findDrink("화요 25");

		// then
		assertThat(found).contains(drink);
	}

	@Test
	void 술_별칭으로_찾는다() {
		// given
		Drink drink = Drink.of("화요25", "화요25", hwayo);
		drink.updateSpec("증류식 소주", new BigDecimal("25.0"), 500, "화요 이십오, Hwayo 25");
		drinkRepository.save(drink);

		// when / then
		assertThat(lookup.findDrink("hwayo25")).contains(drink);
		assertThat(lookup.findDrink("화요이십오")).contains(drink);
	}

	@Test
	void 술이_여럿_걸리면_비어_있다() {
		// given
		drinkRepository.save(Drink.of("화요25", "화요25", hwayo));
		drinkRepository.save(Drink.of("화요 25", "화요25", hwayo));

		// when / then
		assertThat(lookup.findDrink("화요25")).isEmpty();
	}

	@Test
	void 없는_술과_빈_값은_비어_있다() {
		assertThat(lookup.findDrink("없는술")).isEmpty();
		assertThat(lookup.findDrink(null)).isEmpty();
	}

}
