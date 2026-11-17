package com.kdrinker.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class MediaCleanupTest {

	private static final Instant NOW = Instant.parse("2026-11-18T04:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private static final String TOKEN = "A".repeat(43);

	@TempDir
	Path dir;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostPhotoRepository postPhotoRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private PhotoStorage storage;
	private MediaCleanup cleanup;

	@BeforeEach
	void setUp() {
		storage = new PhotoStorage(dir.toString());
		cleanup = new MediaCleanup(postRepository, postPhotoRepository, storage, transactionManager, CLOCK,
				Duration.ofDays(7), Duration.ofDays(1));
	}

	/** 사진 한 장(원본·렌더링 파일 포함)이 달린 게시물을 만든다. */
	private Long postWithPhoto(PostStatus status, Instant updatedAt) {
		Post post = Post.receive(PostSource.TELEGRAM, "향이 좋다", null, CLOCK);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
		Long id = postRepository.saveAndFlush(post).getId();
		String original = storage.saveOriginal(id, 0, "jpg", new byte[] { 1 });
		String rendered = storage.saveRendered(TOKEN, new byte[] { 2 });
		PostPhoto photo = PostPhoto.of(post, 0, original, TOKEN, FitMode.CROP);
		photo.attachRendered(rendered);
		postPhotoRepository.saveAndFlush(photo);
		return id;
	}

	@Test
	void 사진을_7일_지난_취소_게시물의_사진을_지운다() {
		// given
		Long id = postWithPhoto(PostStatus.CANCELED, NOW.minus(Duration.ofDays(7)).minusSeconds(1));

		// when
		cleanup.run();

		// then
		assertThat(postPhotoRepository.findByPostIdOrderByPositionAsc(id)).isEmpty();
		assertThat(Files.exists(dir.resolve("original/" + id + "/0.jpg"))).isFalse();
		assertThat(Files.exists(dir.resolve("original/" + id))).isFalse();
		assertThat(Files.exists(dir.resolve("rendered/" + TOKEN + ".jpg"))).isFalse();
		assertThat(postRepository.findById(id)).isPresent();
	}

	@Test
	void 정확히_7일_전인_취소_게시물은_지우지_않는다() {
		// given
		Long id = postWithPhoto(PostStatus.CANCELED, NOW.minus(Duration.ofDays(7)));

		// when
		cleanup.run();

		// then
		assertThat(postPhotoRepository.findByPostIdOrderByPositionAsc(id)).hasSize(1);
		assertThat(Files.exists(dir.resolve("original/" + id + "/0.jpg"))).isTrue();
		assertThat(Files.exists(dir.resolve("rendered/" + TOKEN + ".jpg"))).isTrue();
	}

	@Test
	void 게시된_게시물의_사진은_지우지_않는다() {
		// given
		Long id = postWithPhoto(PostStatus.PUBLISHED, NOW.minus(Duration.ofDays(30)));

		// when
		cleanup.run();

		// then
		assertThat(postPhotoRepository.findByPostIdOrderByPositionAsc(id)).hasSize(1);
		assertThat(Files.exists(dir.resolve("original/" + id + "/0.jpg"))).isTrue();
	}

	@Test
	void 두_번_돌려도_오류가_없다() {
		// given
		Long id = postWithPhoto(PostStatus.CANCELED, NOW.minus(Duration.ofDays(8)));
		cleanup.run();

		// when / then
		assertThatCode(cleanup::run).doesNotThrowAnyException();
		assertThat(postPhotoRepository.findByPostIdOrderByPositionAsc(id)).isEmpty();
	}

	@Test
	void 하루_지난_임시_폴더도_함께_지운다() throws Exception {
		// given
		String stale = "B".repeat(43);
		storage.saveStaged(stale, "0.jpg", new byte[] { 1 });
		Files.setLastModifiedTime(dir.resolve("tmp/" + stale), java.nio.file.attribute.FileTime
				.from(NOW.minus(Duration.ofDays(1)).minusSeconds(1)));

		// when
		cleanup.run();

		// then
		assertThat(Files.exists(dir.resolve("tmp/" + stale))).isFalse();
	}

}
