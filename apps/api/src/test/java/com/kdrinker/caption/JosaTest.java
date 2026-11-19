package com.kdrinker.caption;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JosaTest {

	@Test
	void 받침에_따라_조사를_고른다() {
		assertThat(Josa.pick("지평 막걸리", "은(는)")).isEqualTo("는");
		assertThat(Josa.pick("청", "은(는)")).isEqualTo("은");
		assertThat(Josa.pick("서울장수", "이(가)")).isEqualTo("가");
		assertThat(Josa.pick("화요", "을(를)")).isEqualTo("를");
		assertThat(Josa.pick("술", "으로(로)")).isEqualTo("로");
		assertThat(Josa.pick("밥", "으로(로)")).isEqualTo("으로");
		assertThat(Josa.pick("탁주", "이에요(예요)")).isEqualTo("예요");
		assertThat(Josa.pick("약주7", "이(가)")).isEqualTo("이");
		assertThat(Josa.pick("ABC", "은(는)")).isEqualTo("는");
	}

	@Test
	void 숫자_1_7_8은_ㄹ_받침이라_으로로에서_로를_고른다() {
		assertThat(Josa.pick("막걸리1", "으로(로)")).isEqualTo("로");
		assertThat(Josa.pick("8", "으로(로)")).isEqualTo("로");
		assertThat(Josa.pick("7", "으로(로)")).isEqualTo("로");
		assertThat(Josa.pick("3", "으로(로)")).isEqualTo("으로");
		assertThat(Josa.pick("0", "으로(로)")).isEqualTo("으로");
		assertThat(Josa.pick("6", "으로(로)")).isEqualTo("으로");
		assertThat(Josa.pick("1", "은(는)")).isEqualTo("은");
	}
}
