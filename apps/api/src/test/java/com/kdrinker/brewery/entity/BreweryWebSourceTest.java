package com.kdrinker.brewery.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BreweryWebSourceTest {

	@Test
	void 공공데이터_양조장은_웹으로_채운_칸만_출처를_돌려준다() {
		// given
		Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);
		brewery.updateContact("경기도 여주시 1", "031-000-0000", null, null, null, "https://data.example.org/1");

		// when
		brewery.recordWebFill(List.of("phone"), "https://a");

		// then
		assertThat(brewery.webSourceOf("phone")).contains("https://a");
		assertThat(brewery.webSourceOf("address")).isEmpty();
	}

	@Test
	void 웹검색_양조장은_값이_있는_칸에_sourceUrl을_돌려준다() {
		// given
		Brewery brewery = Brewery.of("문배주양조원", "문배주양조원", BrewerySource.WEB_SEARCH);
		brewery.updateContact("경기도 김포시 1", null, null, null, null, "https://b");

		// then
		assertThat(brewery.webSourceOf("address")).contains("https://b");
		assertThat(brewery.webSourceOf("phone")).isEmpty();
	}

	@Test
	void 기록은_기존_값과_합치고_빈_목록은_아무것도_하지_않는다() {
		// given
		Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);
		brewery.updateContact("경기도", "031", "https://h", null, null, null);

		// when
		brewery.recordWebFill(List.of(), "https://x");
		brewery.recordWebFill(List.of("phone"), "https://a");
		brewery.recordWebFill(List.of("homepage"), "https://c");

		// then
		assertThat(brewery.getWebFields()).isEqualTo("phone,homepage");
		assertThat(brewery.webSourceOf("phone")).contains("https://c");
		assertThat(brewery.webSourceOf("homepage")).contains("https://c");
	}

}
