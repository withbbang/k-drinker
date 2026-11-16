package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CallbackDataTest {

	@Test
	void parsesActPostIdAndVersion() {
		CallbackData data = CallbackData.parse("ok:128:2").orElseThrow();

		assertThat(data.act()).isEqualTo("ok");
		assertThat(data.postId()).isEqualTo(128L);
		assertThat(data.version()).isEqualTo(2);
	}

	@Test
	void rejectsMalformedData() {
		assertThat(CallbackData.parse("ok:128")).isEmpty();
		assertThat(CallbackData.parse("zz:1:1")).isEmpty();
		assertThat(CallbackData.parse("ok:x:1")).isEmpty();
		assertThat(CallbackData.parse("ok:1:1:1")).isEmpty();
		assertThat(CallbackData.parse(null)).isEmpty();
	}

	@Test
	void worstCaseStaysWithin64Bytes() {
		String data = "ok:9223372036854775807:2147483647";

		assertThat(data.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
		assertThat(CallbackData.parse(data)).isPresent();
	}

}
