package com.kdrinker.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NamesTest {

	@Test
	void 주식회사_약식_표기를_제거한다() {
		assertThat(Names.normalize("(주)화요")).isEqualTo("화요");
		assertThat(Names.normalize("㈜ 배상면주가")).isEqualTo("배상면주가");
	}

	@Test
	void 법인_표기를_제거한다() {
		assertThat(Names.normalize("농업회사법인 주식회사 술샘")).isEqualTo("술샘");
		assertThat(Names.normalize("유한회사 한잔")).isEqualTo("한잔");
	}

	@Test
	void 괄호와_그_안의_내용_공백을_제거한다() {
		assertThat(Names.normalize("영농조합법인 느린마을 양조장(포천)")).isEqualTo("느린마을양조장");
		assertThat(Names.normalize("술[시음용]（전용）{a}")).isEqualTo("술");
	}

	@Test
	void 영문은_소문자로_바꾼다() {
		assertThat(Names.normalize("Hwayo 25")).isEqualTo("hwayo25");
	}

	@Test
	void 빈_값과_null은_빈_문자열이다() {
		assertThat(Names.normalize("  ")).isEqualTo("");
		assertThat(Names.normalize(null)).isEqualTo("");
		assertThat(Names.normalize("")).isEqualTo("");
	}

	@Test
	void 줄바꿈하지_않는_공백과_전각_공백도_지운다() {
		assertThat(Names.normalize("느린마을\u00A0양조장")).isEqualTo("느린마을양조장");
		assertThat(Names.normalize("느린마을\u3000양조장")).isEqualTo("느린마을양조장");
		assertThat(Names.normalize("\u00A0\u3000\u2007\u202F")).isEqualTo("");
	}

}
