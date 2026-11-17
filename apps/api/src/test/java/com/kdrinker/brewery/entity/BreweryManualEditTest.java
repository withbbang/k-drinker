package com.kdrinker.brewery.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BreweryManualEditTest {

	private Brewery webFilled() {
		Brewery brewery = Brewery.of("화요", "화요", BrewerySource.WEB_SEARCH);
		brewery.updateContact("서울", null, null, null, null, "https://src.example");
		brewery.recordWebFill(List.of("address"), "https://web.example");
		return brewery;
	}

	@Test
	void 직접_고치면_MANUAL이고_검증됨이다() {
		// given
		Brewery brewery = webFilled();

		// when
		brewery.editManually("화요 양조", "화요양조", "경기", "", "https://h.example", "hwayo", null);

		// then
		assertThat(brewery.getSource()).isEqualTo(BrewerySource.MANUAL);
		assertThat(brewery.isVerified()).isTrue();
		assertThat(brewery.getName()).isEqualTo("화요 양조");
		assertThat(brewery.getNormalizedName()).isEqualTo("화요양조");
		assertThat(brewery.getAddress()).isEqualTo("경기");
		assertThat(brewery.getPhone()).isNull();
		assertThat(brewery.getInstagramHandle()).isEqualTo("hwayo");
	}

	@Test
	void 직접_고치면_웹_출처가_지워진다() {
		// given
		Brewery brewery = webFilled();
		assertThat(brewery.webSourceOf("address")).isPresent();

		// when
		brewery.editManually("화요", "화요", "서울", null, null, null, null);

		// then
		assertThat(brewery.webSourceOf("address")).isEmpty();
		assertThat(brewery.getSourceUrl()).isNull();
		assertThat(brewery.getWebFields()).isNull();
		assertThat(brewery.getWebSourceUrl()).isNull();
	}

}
