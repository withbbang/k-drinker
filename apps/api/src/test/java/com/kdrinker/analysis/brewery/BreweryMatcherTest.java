package com.kdrinker.analysis.brewery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.brewery.service.BreweryLookup;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({BreweryLookup.class, BreweryMatcher.class})
class BreweryMatcherTest {

	@Autowired
	private BreweryMatcher matcher;

	@Autowired
	private BreweryRepository breweryRepository;

	@Autowired
	private DrinkRepository drinkRepository;

	@MockitoBean
	private BreweryResearcher researcher;

	private static LabelResult label(String drinkName, String breweryName) {
		return new LabelResult(drinkName, null, null, null, breweryName, null, null, null, List.of());
	}

	private static ResearchedBrewery researched(String name) {
		return new ResearchedBrewery(name, "경기도 여주시 1", "031-000-0000", "https://sool.example.com",
				"@sool_official", "https://sool.example.com/about");
	}

	@Test
	void 술이_DB에_있으면_그_양조장을_쓰고_조사하지_않는다() {
		// given
		Brewery hwayo = breweryRepository.save(Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA));
		drinkRepository.save(Drink.of("화요25", "화요25", hwayo));

		// when
		Optional<Brewery> found = matcher.match(label("화요25", "엉뚱한양조장"));

		// then
		assertThat(found).map(Brewery::getId).contains(hwayo.getId());
		verifyNoInteractions(researcher);
	}

	@Test
	void 양조장_이름이_DB에_있으면_조사하지_않는다() {
		// given
		Brewery hwayo = breweryRepository.save(Brewery.of("(주)화요", "화요", BrewerySource.PUBLIC_DATA));

		// when
		Optional<Brewery> found = matcher.match(label("모르는술", "화요"));

		// then
		assertThat(found).map(Brewery::getId).contains(hwayo.getId());
		verifyNoInteractions(researcher);
	}

	@Test
	void DB에_없으면_조사_결과를_미검증_웹검색_양조장으로_저장한다() {
		// given
		when(researcher.research("새술양조장", "새술")).thenReturn(Optional.of(researched("새술 양조장")));

		// when
		Optional<Brewery> found = matcher.match(label("새술", "새술양조장"));

		// then
		assertThat(found).isPresent();
		Brewery saved = breweryRepository.findById(found.get().getId()).orElseThrow();
		assertThat(saved.getName()).isEqualTo("새술 양조장");
		assertThat(saved.getNormalizedName()).isEqualTo("새술양조장");
		assertThat(saved.getSource()).isEqualTo(BrewerySource.WEB_SEARCH);
		assertThat(saved.isVerified()).isFalse();
		assertThat(saved.getSourceUrl()).isEqualTo("https://sool.example.com/about");
		assertThat(saved.getAddress()).isEqualTo("경기도 여주시 1");
		assertThat(saved.getPhone()).isEqualTo("031-000-0000");
		assertThat(saved.getHomepage()).isEqualTo("https://sool.example.com");
		assertThat(saved.getInstagramHandle()).isEqualTo("sool_official");
	}

	@Test
	void 조사한_이름이_이미_있으면_가장_작은_id를_쓰고_덮어쓰지_않는다() {
		// given
		Brewery first = breweryRepository.save(Brewery.of("새술양조장", "새술양조장", BrewerySource.PUBLIC_DATA));
		breweryRepository.save(Brewery.of("새술 양조장", "새술양조장", BrewerySource.PUBLIC_DATA));
		when(researcher.research("다른이름", null)).thenReturn(Optional.of(researched("새술 양조장")));
		long before = breweryRepository.count();

		// when
		Optional<Brewery> found = matcher.match(label(null, "다른이름"));

		// then
		assertThat(found).map(Brewery::getId).contains(first.getId());
		assertThat(breweryRepository.count()).isEqualTo(before);
		Brewery kept = breweryRepository.findById(first.getId()).orElseThrow();
		assertThat(kept.getSource()).isEqualTo(BrewerySource.PUBLIC_DATA);
		assertThat(kept.getSourceUrl()).isNull();
	}

	@Test
	void 조사_결과가_없으면_비어_있고_저장하지_않는다() {
		// given
		when(researcher.research(any(), any())).thenReturn(Optional.empty());
		long before = breweryRepository.count();

		// when
		Optional<Brewery> found = matcher.match(label("없는술", "없는양조장"));

		// then
		assertThat(found).isEmpty();
		assertThat(breweryRepository.count()).isEqualTo(before);
		verify(researcher).research("없는양조장", "없는술");
	}

	@Test
	void 술_이름만_있어도_조사한다() {
		// given
		when(researcher.research(null, "없는술")).thenReturn(Optional.empty());

		// when
		matcher.match(label("없는술", null));

		// then
		verify(researcher).research(null, "없는술");
	}

	@Test
	void 술과_양조장_이름이_모두_없으면_조사하지_않는다() {
		// when
		Optional<Brewery> found = matcher.match(label(null, null));

		// then
		assertThat(found).isEmpty();
		verifyNoInteractions(researcher);
	}

	@Test
	void 이름이_공백뿐이어도_조사하지_않는다() {
		// when
		Optional<Brewery> found = matcher.match(label("  ", " "));

		// then
		assertThat(found).isEmpty();
		verifyNoInteractions(researcher);
	}
}
