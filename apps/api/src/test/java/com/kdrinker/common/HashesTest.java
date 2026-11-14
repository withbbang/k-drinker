package com.kdrinker.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HashesTest {

	@Test
	void abc의_sha256_16진수를_돌려준다() {
		// when
		String hex = Hashes.sha256Hex("abc".getBytes(StandardCharsets.UTF_8));

		// then
		assertThat(hex).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void 빈_입력의_sha256도_계산한다() {
		// when / then
		assertThat(Hashes.sha256Hex(new byte[0]))
				.isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
	}

}
