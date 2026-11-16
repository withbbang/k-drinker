package com.kdrinker.analysis.brewery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kdrinker.analysis.claude.ClaudeUnavailableException;
import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.brewery.service.BreweryLookup;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({BreweryLookup.class, BreweryMatcher.class, BreweryMatcherTest.FixedClock.class})
class BreweryMatcherTest {

	private static final Instant NOW = Instant.parse("2026-11-15T03:00:00Z");

	@TestConfiguration
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

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

	/** 연락처 네 칸이 모두 찬 양조장. 빈 칸 보강 조사가 돌지 않는다. */
	private Brewery complete(String name, String normalizedName) {
		Brewery brewery = Brewery.of(name, normalizedName, BrewerySource.PUBLIC_DATA);
		brewery.updateContact("경기도 여주시 2", "031-111-1111", "https://hwayo.example.com", "hwayo", null, null);
		brewery.markVerified();
		return breweryRepository.save(brewery);
	}

	@Test
	void 술이_DB에_있으면_그_양조장을_쓰고_조사하지_않는다() {
		// given
		Brewery hwayo = complete("화요", "화요");
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
		Brewery hwayo = complete("(주)화요", "화요");

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
		assertThat(saved.getResearchedAt()).isEqualTo(NOW);
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

	@Test
	void 긴_홈페이지와_출처_URL도_저장된다() {
		// given
		String longUrl = "https://sool.example.com/" + "%EC%88%A0".repeat(100);
		when(researcher.research(any(), any())).thenReturn(Optional.of(new ResearchedBrewery("새술양조장", null, null,
				longUrl, null, longUrl)));

		// when
		Brewery saved = matcher.match(label("새술", "새술양조장")).orElseThrow();
		breweryRepository.flush();

		// then
		assertThat(saved.getHomepage()).isEqualTo(longUrl);
		assertThat(saved.getSourceUrl()).isEqualTo(longUrl);
	}

	@Test
	void 컬럼_길이를_넘는_조사_값은_버리고_저장한다() {
		// given
		String tooLongUrl = "https://sool.example.com/" + "a".repeat(1000);
		when(researcher.research(any(), any())).thenReturn(Optional.of(new ResearchedBrewery("새술양조장",
				"주".repeat(256), "0".repeat(256), tooLongUrl, null, tooLongUrl)));

		// when
		Brewery saved = matcher.match(label("새술", "새술양조장")).orElseThrow();
		breweryRepository.flush();

		// then
		assertThat(saved.getAddress()).isNull();
		assertThat(saved.getPhone()).isNull();
		assertThat(saved.getHomepage()).isNull();
		assertThat(saved.getSourceUrl()).isNull();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 조사_중에는_트랜잭션을_잡고_있지_않다() {
		// given
		boolean[] inTx = new boolean[1];
		when(researcher.research(any(), any())).thenAnswer(invocation -> {
			inTx[0] = TransactionSynchronizationManager.isActualTransactionActive();
			return Optional.empty();
		});

		// when
		matcher.match(label("새술", "새술양조장"));

		// then
		assertThat(inTx[0]).isFalse();
	}

	// --- 빈 칸 보강 ---

	/** 주소만 있고 전화·홈페이지·인스타그램이 빈 공공데이터 양조장. */
	private Brewery withBlanks() {
		Brewery brewery = Brewery.of("새술양조장", "새술양조장", BrewerySource.PUBLIC_DATA);
		brewery.updateContact("경기도 이천시 9", null, " ", null, null, "https://data.example.org/1");
		brewery.markVerified();
		return breweryRepository.save(brewery);
	}

	@Test
	void DB_양조장에_빈_칸이_있으면_조사해_빈_칸만_채운다() {
		// given
		Brewery known = withBlanks();
		when(researcher.research("새술양조장", "새술")).thenReturn(Optional.of(researched("새술 양조장")));

		// when
		Brewery found = matcher.match(label("새술", "새술양조장")).orElseThrow();

		// then
		Brewery saved = breweryRepository.findById(known.getId()).orElseThrow();
		assertThat(found.getId()).isEqualTo(known.getId());
		assertThat(saved.getPhone()).isEqualTo("031-000-0000");
		assertThat(saved.getHomepage()).isEqualTo("https://sool.example.com");
		assertThat(saved.getInstagramHandle()).isEqualTo("sool_official");
		assertThat(saved.getResearchedAt()).isEqualTo(NOW);
		assertThat(found.getPhone()).isEqualTo("031-000-0000");
	}

	@Test
	void 빈_칸_보강은_값이_있는_칸과_출처_검증_여부를_덮어쓰지_않는다() {
		// given
		Brewery known = withBlanks();
		when(researcher.research(any(), any())).thenReturn(Optional.of(researched("새술양조장")));

		// when
		matcher.match(label("새술", "새술양조장"));

		// then
		Brewery saved = breweryRepository.findById(known.getId()).orElseThrow();
		assertThat(saved.getAddress()).isEqualTo("경기도 이천시 9");
		assertThat(saved.getSourceUrl()).isEqualTo("https://data.example.org/1");
		assertThat(saved.getSource()).isEqualTo(BrewerySource.PUBLIC_DATA);
		assertThat(saved.isVerified()).isTrue();
	}

	@Test
	void 같은_양조장으로_다시_게시하면_다시_조사하지_않는다() {
		// given
		withBlanks();
		when(researcher.research(any(), any())).thenReturn(Optional.empty());

		// when
		matcher.match(label("새술", "새술양조장"));
		matcher.match(label("새술", "새술양조장"));

		// then
		verify(researcher, times(1)).research(any(), any());
	}

	@Test
	void 조사_결과가_없어도_조사_시각을_남긴다() {
		// given
		Brewery known = withBlanks();
		when(researcher.research(any(), any())).thenReturn(Optional.empty());

		// when
		Optional<Brewery> found = matcher.match(label("새술", "새술양조장"));

		// then
		assertThat(found).map(Brewery::getId).contains(known.getId());
		Brewery saved = breweryRepository.findById(known.getId()).orElseThrow();
		assertThat(saved.getResearchedAt()).isEqualTo(NOW);
		assertThat(saved.getPhone()).isNull();
	}

	@Test
	void 조사한_이름이_DB_양조장과_다르면_채우지_않는다() {
		// given
		Brewery known = withBlanks();
		when(researcher.research(any(), any())).thenReturn(Optional.of(researched("엉뚱한양조장")));

		// when
		matcher.match(label("새술", "새술양조장"));

		// then
		Brewery saved = breweryRepository.findById(known.getId()).orElseThrow();
		assertThat(saved.getPhone()).isNull();
		assertThat(saved.getInstagramHandle()).isNull();
		assertThat(saved.getResearchedAt()).isEqualTo(NOW);
		assertThat(breweryRepository.findByNormalizedName("엉뚱한양조장")).isEmpty();
	}

	@Test
	void 빈_칸_보강도_검증을_거친_값만_채운다() {
		// given
		Brewery known = withBlanks();
		when(researcher.research(any(), any())).thenReturn(Optional.of(new ResearchedBrewery("새술양조장", null,
				"0".repeat(256), "javascript:alert(1)", "not a handle!", "https://sool.example.com/about")));

		// when
		matcher.match(label("새술", "새술양조장"));

		// then
		Brewery saved = breweryRepository.findById(known.getId()).orElseThrow();
		assertThat(saved.getPhone()).isNull();
		assertThat(saved.getHomepage()).isBlank();
		assertThat(saved.getInstagramHandle()).isNull();
	}

	@Test
	void 빈_칸_보강_중_Claude_오류면_DB_양조장을_그대로_쓰고_조사_시각을_남기지_않는다() {
		// given
		Brewery known = withBlanks();
		when(researcher.research(any(), any())).thenThrow(new ClaudeUnavailableException("시간 초과"));

		// when
		Optional<Brewery> found = matcher.match(label("새술", "새술양조장"));

		// then
		assertThat(found).map(Brewery::getId).contains(known.getId());
		Brewery saved = breweryRepository.findById(known.getId()).orElseThrow();
		assertThat(saved.getResearchedAt()).isNull();
		assertThat(saved.getPhone()).isNull();
	}

	@Test
	void 빈_칸이_없는_양조장은_조사하지_않는다() {
		// given
		complete("새술양조장", "새술양조장");

		// when
		matcher.match(label("새술", "새술양조장"));

		// then
		verify(researcher, never()).research(any(), any());
	}

	@Test
	void 이미_조사한_양조장은_빈_칸이_있어도_조사하지_않는다() {
		// given
		Brewery known = Brewery.of("새술양조장", "새술양조장", BrewerySource.PUBLIC_DATA);
		known.markResearched(NOW.minusSeconds(3600));
		breweryRepository.save(known);

		// when
		matcher.match(label("새술", "새술양조장"));

		// then
		verifyNoInteractions(researcher);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 빈_칸_보강_조사_중에도_트랜잭션을_잡고_있지_않다() {
		// given
		Long id = withBlanks().getId();
		boolean[] inTx = new boolean[1];
		when(researcher.research(any(), any())).thenAnswer(invocation -> {
			inTx[0] = TransactionSynchronizationManager.isActualTransactionActive();
			return Optional.of(researched("새술양조장"));
		});

		try {
			// when
			Brewery found = matcher.match(label("새술", "새술양조장")).orElseThrow();

			// then
			assertThat(inTx[0]).isFalse();
			assertThat(found.getPhone()).isEqualTo("031-000-0000");
			Brewery committed = breweryRepository.findById(id).orElseThrow();
			assertThat(committed.getPhone()).isEqualTo("031-000-0000");
			assertThat(committed.getResearchedAt()).isEqualTo(NOW);
		} finally {
			breweryRepository.deleteById(id);
		}
	}

	// --- 이름이 같은 양조장이 여럿 ---

	@Test
	void 정규화_이름이_같은_양조장이_여럿이면_조사하지_않고_가장_작은_id를_쓴다() {
		// given
		Brewery first = complete("같은양조장", "같은양조장");
		complete("같은 양조장", "같은양조장");

		// when
		Optional<Brewery> found = matcher.match(label("모르는술", "같은 양조장"));

		// then
		assertThat(found).map(Brewery::getId).contains(first.getId());
		verifyNoInteractions(researcher);
	}

	@Test
	void 이름이_같은_양조장이_여럿이어도_가장_작은_id의_빈_칸은_보강한다() {
		// given
		Brewery first = withBlanks();
		complete("새술 양조장", "새술양조장");
		when(researcher.research(any(), any())).thenReturn(Optional.of(researched("새술양조장")));

		// when
		Optional<Brewery> found = matcher.match(label("모르는술", "새술양조장"));

		// then
		assertThat(found).map(Brewery::getId).contains(first.getId());
		assertThat(breweryRepository.findById(first.getId()).orElseThrow().getPhone()).isEqualTo("031-000-0000");
		verify(researcher, times(1)).research(any(), any());
	}
}
