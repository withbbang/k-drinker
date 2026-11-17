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

	@Test
	void 스테이징_파일을_최종_경로로_옮기면_원본_자리에서_사라진다() throws Exception {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());
		String staged = storage.saveStaged(TOKEN, "0.jpg", new byte[] { 4, 5 });

		// when
		String moved = storage.promote(staged, PhotoStorage.originalPath(3L, 0, "jpg"));

		// then
		assertThat(moved).isEqualTo("original/3/0.jpg");
		assertThat(storage.read(moved)).containsExactly(4, 5);
		assertThat(Files.exists(dir.resolve(staged))).isFalse();
	}

	@Test
	void 스테이징_디렉터리를_통째로_지운다() throws Exception {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());
		storage.saveStaged(TOKEN, "0.jpg", new byte[] { 1 });
		storage.saveStaged(TOKEN, "0.rendered.jpg", new byte[] { 2 });

		// when
		storage.deleteStagingQuietly(TOKEN);

		// then
		assertThat(Files.exists(dir.resolve("tmp/" + TOKEN))).isFalse();
	}

	@Test
	void 스테이징_이름에_경로_조작이_있으면_거절한다() {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());

		// when / then
		assertThatThrownBy(() -> storage.saveStaged(TOKEN, "../x.jpg", new byte[] { 1 }))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.saveStaged("../../x", "0.jpg", new byte[] { 1 }))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 하루_지난_임시_폴더만_지운다() throws Exception {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());
		String stale = "B".repeat(43);
		String fresh = "C".repeat(43);
		storage.saveStaged(stale, "0.jpg", new byte[] { 1 });
		storage.saveStaged(fresh, "0.jpg", new byte[] { 2 });
		java.time.Instant cutoff = java.time.Instant.parse("2026-11-17T04:00:00Z");
		Files.setLastModifiedTime(dir.resolve("tmp/" + stale),
				java.nio.file.attribute.FileTime.from(cutoff.minusSeconds(1)));
		Files.setLastModifiedTime(dir.resolve("tmp/" + fresh), java.nio.file.attribute.FileTime.from(cutoff));

		// when
		int deleted = storage.deleteStagingOlderThan(cutoff);

		// then
		assertThat(deleted).isEqualTo(1);
		assertThat(Files.exists(dir.resolve("tmp/" + stale))).isFalse();
		assertThat(Files.exists(dir.resolve("tmp/" + fresh + "/0.jpg"))).isTrue();
	}

	@Test
	void tmp_폴더가_없어도_임시_폴더_정리는_0을_돌려준다() {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());

		// when / then
		assertThat(storage.deleteStagingOlderThan(java.time.Instant.now())).isZero();
	}

	@Test
	void 게시물_원본_폴더를_통째로_지우고_없어도_오류가_없다() {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());
		storage.saveOriginal(9L, 0, "jpg", new byte[] { 1 });

		// when
		storage.deletePostOriginalDirQuietly(9L);
		storage.deletePostOriginalDirQuietly(9L);

		// then
		assertThat(Files.exists(dir.resolve("original/9"))).isFalse();
	}

}
