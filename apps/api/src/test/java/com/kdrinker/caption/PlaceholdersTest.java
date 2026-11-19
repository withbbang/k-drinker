package com.kdrinker.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.drink.entity.Drink;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {

	private static Brewery brewery(String name, String address, String handle) {
		Brewery b = Brewery.of(name, name, BrewerySource.PUBLIC_DATA);
		b.updateContact(address, null, null, handle, null, null);
		return b;
	}

	private static Drink drink(String name, String category, BigDecimal abv, Brewery b) {
		Drink d = Drink.of(name, name, b);
		d.updateSpec(category, abv, null, null);
		return d;
	}

	@Test
	void fill_replacesWithParticle() {
		CaptionValues v = CaptionValues.of(drink("지평 막걸리", "탁주", null, brewery("지평주조", null, null)), null);
		assertThat(Placeholders.fill("{술이름}은(는) {양조장}에서 만든 {종류}예요.", v))
				.isEqualTo("지평 막걸리는 지평주조에서 만든 탁주예요.");
	}

	@Test
	void fill_dropsLineWithEmptyPlaceholder() {
		CaptionValues v = CaptionValues.of(drink("술", "탁주", null, null), null);
		assertThat(Placeholders.fill("첫 줄\n{도수}, {용량}\n끝", v)).isEqualTo("첫 줄\n끝");
	}

	@Test
	void fill_dropsOnlyEmptyHashtag() {
		CaptionValues v = CaptionValues.of("지평막걸리", null, null, null);
		assertThat(Placeholders.fill("#전통주 #{술이름태그} #{양조장태그}", v)).isEqualTo("#전통주 #지평막걸리");
	}

	@Test
	void fill_dedupesHashtags() {
		CaptionValues v = CaptionValues.of("막걸리", null, null, null);
		assertThat(Placeholders.fill("#막걸리 #{술이름태그}", v)).isEqualTo("#막걸리");
	}

	@Test
	void fill_keepsUnknownBraces() {
		assertThat(Placeholders.fill("{메뉴}는 파전", CaptionValues.EMPTY)).isEqualTo("{메뉴}는 파전");
	}

	@Test
	void fill_breweryBlockLines() {
		String m = BreweryBlock.MARKER;
		String stored = m + "\n🍶 {양조장}\n📍 {주소}\n📷 {인스타그램}\n{기타SNS}\n" + m;
		CaptionValues v = CaptionValues.of("술", null, brewery("지평주조", null, "jipyeong"), null);
		assertThat(Placeholders.fill(stored, v)).isEqualTo(m + "\n🍶 지평주조\n📷 @jipyeong\n" + m);
	}

	@Test
	void fill_legacyCaptionUnchanged() {
		String legacy = "<!--brewery-->\n🍶 화요\n<!--brewery-->\n\n\n#전통주 {메뉴}";
		assertThat(Placeholders.fill(legacy, CaptionValues.EMPTY)).isEqualTo(legacy);
	}

	@Test
	void isTemplate() {
		assertThat(Placeholders.isTemplate("{술이름}")).isTrue();
		assertThat(Placeholders.isTemplate("{메뉴}")).isFalse();
		assertThat(Placeholders.isTemplate(null)).isFalse();
	}
}
