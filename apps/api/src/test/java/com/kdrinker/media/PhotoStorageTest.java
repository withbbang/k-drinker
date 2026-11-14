package com.kdrinker.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhotoStorageTest {

	private static final String TOKEN = "A".repeat(43);

	@TempDir
	Path dir;

	@Test
	void 원본과_렌더링_경로가_MEDIA_DIR_안에_있다() throws Exception {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());

		// when
		String original = storage.saveOriginal(7L, 2, "png", new byte[] { 1, 2 });
		String rendered = storage.saveRendered(TOKEN, new byte[] { 3 });

		// then
		assertThat(original).isEqualTo("original/7/2.png");
		assertThat(rendered).isEqualTo("rendered/" + TOKEN + ".jpg");
		assertThat(Files.readAllBytes(dir.resolve(original))).containsExactly(1, 2);
		assertThat(storage.read(rendered)).containsExactly(3);
	}

	@Test
	void 렌더링_파일을_덮어써도_임시_파일이_남지_않는다() throws Exception {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());
		storage.saveRendered(TOKEN, new byte[] { 1 });

		// when
		storage.saveRendered(TOKEN, new byte[] { 2, 3 });

		// then
		assertThat(storage.read("rendered/" + TOKEN + ".jpg")).containsExactly(2, 3);
		try (var files = Files.list(dir.resolve("rendered"))) {
			assertThat(files.toList()).hasSize(1);
		}
	}

	@Test
	void 저장된_경로가_MEDIA_DIR_밖을_가리키면_읽지_않는다() {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());

		// when / then
		assertThatThrownBy(() -> storage.read("../outside.txt")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 형식이_틀린_토큰으로는_렌더링_파일을_쓰지_않는다() {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());

		// when / then
		assertThatThrownBy(() -> storage.saveRendered("../../x", new byte[] { 1 }))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
