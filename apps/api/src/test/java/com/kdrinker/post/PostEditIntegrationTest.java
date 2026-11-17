package com.kdrinker.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdrinker.media.PhotoStorage;
import com.kdrinker.post.dto.PostUpdateRequest;
import com.kdrinker.post.dto.PostUpdateRequest.PhotoEdit;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.post.service.PostEditService;
import com.kdrinker.telegram.PostNotifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/** 실제 DB·파일로 사진 삭제·순서 바꾸기와 롤백 시 파일 보존을 확인한다. 텔레그램 알림은 목이다. */
@SpringBootTest(properties = "app.scheduling.enabled=false")
class PostEditIntegrationTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC);
	private static final Path MEDIA_DIR = createMediaDir();

	@DynamicPropertySource
	static void mediaDir(DynamicPropertyRegistry registry) {
		registry.add("app.media.dir", MEDIA_DIR::toString);
	}

	@Autowired
	private PostEditService postEditService;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostPhotoRepository postPhotoRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private PostNotifier postNotifier;

	private static Path createMediaDir() {
		try {
			return Files.createTempDirectory("post-edit-media");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@BeforeEach
	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
	}

	private record Saved(Long postId, int version, List<PostPhoto> photos) {
	}

	/** 사진 3장(원본·렌더링 파일 포함)이 있는 검토 대기 게시물. */
	private Saved savePost() throws IOException {
		Post post = Post.receive(PostSource.SHARE, "메모", null, CLOCK);
		ReflectionTestUtils.setField(post, "status", PostStatus.PENDING_REVIEW);
		post.writeCaption("훅", "", null, null);
		Long postId = postRepository.saveAndFlush(post).getId();
		List<PostPhoto> photos = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			String token = String.valueOf((char) ('a' + i)).repeat(43);
			String original = PhotoStorage.originalPath(postId, i, "jpg");
			String rendered = PhotoStorage.renderedPath(token);
			write(original);
			write(rendered);
			PostPhoto photo = PostPhoto.of(postRepository.getReferenceById(postId), i, original, token, FitMode.CROP);
			photo.attachRendered(rendered);
			photos.add(postPhotoRepository.saveAndFlush(photo));
		}
		return new Saved(postId, postRepository.findById(postId).orElseThrow().getVersion(), photos);
	}

	private static void write(String relative) throws IOException {
		Path path = MEDIA_DIR.resolve(relative);
		Files.createDirectories(path.getParent());
		Files.write(path, new byte[] { 1, 2, 3 });
	}

	private static PostUpdateRequest request(int version, List<PhotoEdit> photos) {
		return new PostUpdateRequest(version, null, null, null, null, null, "메모", "훅", "", photos);
	}

	@Test
	void 빠진_사진은_행과_파일이_지워지고_순서가_다시_매겨진다() throws IOException {
		// given: 가운데 사진을 빼고 남은 두 장의 순서를 뒤집는다
		Saved saved = savePost();
		PostPhoto first = saved.photos().get(0);
		PostPhoto middle = saved.photos().get(1);
		PostPhoto last = saved.photos().get(2);
		PostUpdateRequest req = request(saved.version(),
				List.of(new PhotoEdit(last.getId(), FitMode.CROP), new PhotoEdit(first.getId(), FitMode.CROP)));

		// when
		postEditService.update(saved.postId(), req);

		// then
		List<PostPhoto> after = postPhotoRepository.findByPostIdOrderByPositionAsc(saved.postId());
		assertThat(after).extracting(PostPhoto::getId).containsExactly(last.getId(), first.getId());
		assertThat(after).extracting(PostPhoto::getPosition).containsExactly(0, 1);
		assertThat(MEDIA_DIR.resolve(middle.getRenderedPath())).doesNotExist();
		assertThat(MEDIA_DIR.resolve(middle.getOriginalPath())).doesNotExist();
		assertThat(MEDIA_DIR.resolve(first.getRenderedPath())).exists();
		Post post = postRepository.findById(saved.postId()).orElseThrow();
		assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(post.getVersion()).isEqualTo(saved.version() + 1);
	}

	@Test
	void 롤백되면_파일이_그대로다() throws IOException {
		// given: 가운데 사진을 빼지만, 남은 사진의 원본이 없어 다시 렌더링이 실패한다
		Saved saved = savePost();
		PostPhoto first = saved.photos().get(0);
		PostPhoto middle = saved.photos().get(1);
		Files.delete(MEDIA_DIR.resolve(first.getOriginalPath()));
		PostUpdateRequest req = request(saved.version(), List.of(new PhotoEdit(first.getId(), FitMode.PAD),
				new PhotoEdit(saved.photos().get(2).getId(), FitMode.CROP)));

		// when
		assertThatThrownBy(() -> postEditService.update(saved.postId(), req));

		// then
		assertThat(MEDIA_DIR.resolve(middle.getRenderedPath())).exists();
		assertThat(MEDIA_DIR.resolve(middle.getOriginalPath())).exists();
		assertThat(postPhotoRepository.findByPostIdOrderByPositionAsc(saved.postId())).hasSize(3);
		assertThat(postRepository.findById(saved.postId()).orElseThrow().getVersion()).isEqualTo(saved.version());
	}
}
