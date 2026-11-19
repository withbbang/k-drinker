package com.kdrinker.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import org.junit.jupiter.api.Test;

class BreweryBlockTest {

	private static Brewery brewery(String name, String address, String phone, String homepage, String handle) {
		Brewery b = Brewery.of(name, name, BrewerySource.PUBLIC_DATA);
		b.updateContact(address, phone, homepage, handle, null, null);
		return b;
	}

	@Test
	void 이름만_있으면_이름_한_줄만_남는다() {
		assertThat(BreweryBlock.render(brewery("화요", null, null, null, null)))
				.isEqualTo("<!--brewery-->\n🍶 화요\n<!--brewery-->");
	}

	@Test
	void 홈페이지는_스킴과_끝_슬래시가_빠진다() {
		assertThat(BreweryBlock.render(brewery("화요", null, null, "https://hwayo.com/", null)))
				.contains("🏠 hwayo.com\n");
		assertThat(BreweryBlock.render(brewery("화요", null, null, "http://hwayo.com", null)))
				.contains("🏠 hwayo.com\n");
	}

	@Test
	void 핸들에는_골뱅이가_붙고_이미_있으면_중복되지_않는다() {
		assertThat(BreweryBlock.render(brewery("화요", null, null, null, "hwayo_official")))
				.contains("📷 @hwayo_official\n");
		assertThat(BreweryBlock.render(brewery("화요", null, null, null, "@hwayo_official")))
				.contains("📷 @hwayo_official\n");
	}

	@Test
	void 모든_값이_있으면_정해진_순서로_나온다() {
		assertThat(BreweryBlock.render(brewery("화요", "경기 여주시", "031-1", "hwayo.com", "hwayo_official")))
				.isEqualTo("<!--brewery-->\n🍶 화요\n📍 경기 여주시\n☎ 031-1\n🏠 hwayo.com\n📷 @hwayo_official\n<!--brewery-->");
	}

	@Test
	void 양조장이_없으면_마커_두_줄만_나온다() {
		assertThat(BreweryBlock.render(null)).isEqualTo("<!--brewery-->\n<!--brewery-->");
	}

	@Test
	void 틀은_마커_사이에_이름표_줄이_정해진_순서로_있다() {
		assertThat(BreweryBlock.TEMPLATE).isEqualTo(
				"<!--brewery-->\n🍶 {양조장}\n📍 {주소}\n☎ {전화}\n🏠 {홈페이지}\n📷 {인스타그램}\n{기타SNS}\n<!--brewery-->");
	}
}
