package com.kdrinker.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.drink.entity.Drink;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptionValuesTest {

	@Test
	void asMap_formatsValues() {
		Brewery b = Brewery.of("지평주조", "지평주조", BrewerySource.PUBLIC_DATA);
		b.updateContact(null, null, "https://a.com/", "@@x", null, null);
		Drink d = Drink.of("지평 막걸리", "지평막걸리", b);
		d.updateSpec("탁주", new BigDecimal("6.50"), 750, null);

		Map<String, String> m = CaptionValues.of(d, new BigDecimal("4")).asMap();

		assertThat(m).containsEntry("도수", "6.5%").containsEntry("용량", "750ml").containsEntry("별점", "★4.0")
				.containsEntry("홈페이지", "a.com").containsEntry("인스타그램", "@x")
				.containsEntry("술이름태그", "지평막걸리").containsEntry("양조장태그", "지평주조")
				.doesNotContainKey("주소");
		assertThat(CaptionValues.EMPTY.asMap()).isEmpty();
	}
}
