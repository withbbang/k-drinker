package com.kdrinker.brewery.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BreweryAlias;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryAliasRepository;
import com.kdrinker.brewery.repository.BreweryRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(BreweryCsvImporter.class)
class BreweryCsvImporterTest {

	private static final String HEADER = "name,address,phone,homepage,instagram,aliases,source_url\n";

	@Autowired
	private BreweryCsvImporter importer;

	@Autowired
	private BreweryRepository breweryRepository;

	@Autowired
	private BreweryAliasRepository aliasRepository;

	private Reader fixture(String name, Charset charset) {
		return new InputStreamReader(getClass().getResourceAsStream("/brewery/" + name), charset);
	}

	@Test
	void UTF8_파일_3행을_적재하면_공공데이터_검증됨으로_저장된다() throws IOException {
		// when
		ImportReport report = importer.importFrom(fixture("sample-utf8.csv", StandardCharsets.UTF_8), "sample");

		// then
		assertThat(report.inserted()).isEqualTo(3);
		assertThat(report.updated()).isZero();
		assertThat(report.skipped()).isZero();
		Brewery b = breweryRepository.findByNormalizedName("배상면주가").get(0);
		assertThat(b.getSource()).isEqualTo(BrewerySource.PUBLIC_DATA);
		assertThat(b.isVerified()).isTrue();
		assertThat(b.getSourceUrl()).isEqualTo("https://example.org/a");
		assertThat(b.getInstagramHandle()).isEqualTo("baesangmyun");
		assertThat(b.getAddress()).isEqualTo("경기도 포천시");
		assertThat(breweryRepository.findByNormalizedName("국순당")).hasSize(1);
	}

	@Test
	void 같은_파일을_다시_적재하면_갱신만_되고_행이_늘지_않는다() throws IOException {
		// given
		importer.importFrom(fixture("sample-utf8.csv", StandardCharsets.UTF_8), "sample");

		// when
		ImportReport report = importer.importFrom(fixture("sample-utf8.csv", StandardCharsets.UTF_8), "sample");

		// then
		assertThat(report.inserted()).isZero();
		assertThat(report.updated()).isEqualTo(3);
		assertThat(breweryRepository.count()).isEqualTo(3);
		assertThat(aliasRepository.count()).isEqualTo(3);
	}

	@Test
	void 별칭은_정규화해_저장하고_빈_항목은_건너뛴다() throws IOException {
		// when
		importer.importFrom(fixture("sample-utf8.csv", StandardCharsets.UTF_8), "sample");

		// then
		Brewery b = breweryRepository.findByNormalizedName("국순당").get(0);
		List<BreweryAlias> aliases = aliasRepository.findByNormalizedAlias("국순당횡성");
		assertThat(aliases).hasSize(1);
		assertThat(aliases.get(0).getBrewery().getId()).isEqualTo(b.getId());
		assertThat(aliasRepository.count()).isEqualTo(3);
	}

	@Test
	void name이_빈_행은_사유와_함께_건너뛴다() throws IOException {
		// given
		String csv = HEADER + ",서울,,,,,\n한강주조,,,,,,\n";

		// when
		ImportReport report = importer.importFrom(new StringReader(csv), "t");

		// then
		assertThat(report.inserted()).isEqualTo(1);
		assertThat(report.skipped()).isEqualTo(1);
		assertThat(report.skippedReasons()).hasSize(1);
		assertThat(report.skippedReasons().get(0)).contains("name");
	}

	@Test
	void 기존_웹검색_양조장은_공공데이터로_덮고_검증됨이_된다() throws IOException {
		// given
		breweryRepository.save(Brewery.of("배상면주가", "배상면주가", BrewerySource.WEB_SEARCH));

		// when
		ImportReport report = importer.importFrom(new StringReader(
				HEADER + "배상면주가,경기도,031,,@baek,,https://example.org/a\n"), "t");

		// then
		assertThat(report.updated()).isEqualTo(1);
		Brewery b = breweryRepository.findByNormalizedName("배상면주가").get(0);
		assertThat(b.getSource()).isEqualTo(BrewerySource.PUBLIC_DATA);
		assertThat(b.isVerified()).isTrue();
		assertThat(b.getInstagramHandle()).isEqualTo("baek");
	}

	@Test
	void 다시_적재해도_웹_조사_시각은_그대로_둔다() throws IOException {
		// given
		Instant researchedAt = Instant.parse("2026-11-15T03:00:00Z");
		Brewery existing = Brewery.of("배상면주가", "배상면주가", BrewerySource.PUBLIC_DATA);
		existing.markResearched(researchedAt);
		breweryRepository.save(existing);

		// when
		importer.importFrom(new StringReader(HEADER + "배상면주가,경기도,031,,,,https://example.org/a\n"), "t");

		// then
		assertThat(breweryRepository.findByNormalizedName("배상면주가").get(0).getResearchedAt())
				.isEqualTo(researchedAt);
	}

	@Test
	void 같은_정규화_이름이_여럿이면_id가_가장_작은_하나만_갱신한다() throws IOException {
		// given
		Brewery first = breweryRepository.save(Brewery.of("국순당", "국순당", BrewerySource.MANUAL));
		breweryRepository.save(Brewery.of("(주)국순당", "국순당", BrewerySource.MANUAL));

		// when
		ImportReport report = importer.importFrom(new StringReader(HEADER + "국순당,강원,,,,,\n"), "t");

		// then
		assertThat(report.inserted()).isZero();
		assertThat(report.updated()).isEqualTo(1);
		assertThat(breweryRepository.count()).isEqualTo(2);
		assertThat(breweryRepository.findById(first.getId()).orElseThrow().getAddress()).isEqualTo("강원");
	}

	@Test
	void CP949_파일을_읽어도_한글_이름이_그대로_저장된다() throws IOException {
		// when
		importer.importFrom(fixture("sample-cp949.csv", Charset.forName("CP949")), "cp949");

		// then
		assertThat(breweryRepository.findAll()).extracting(Brewery::getName).containsExactly("배상면주가");
	}

	@Test
	void 헤더에_알_수_없는_열이_있으면_예외이고_아무것도_저장하지_않는다() {
		// given
		String csv = "nmae,address\n배상면주가,경기도\n";

		// when / then
		assertThatThrownBy(() -> importer.importFrom(new StringReader(csv), "t"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(breweryRepository.count()).isZero();
	}

	@Test
	void name_헤더가_없으면_예외다() {
		assertThatThrownBy(() -> importer.importFrom(new StringReader("address\n경기도\n"), "t"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void UTF8_BOM이_붙은_헤더도_읽는다() throws IOException {
		// when
		ImportReport report = importer.importFrom(new StringReader("﻿name,address\n한강주조,서울\n"), "t");

		// then
		assertThat(report.inserted()).isEqualTo(1);
	}

}
