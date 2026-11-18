package com.kdrinker.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.media.ImageDecoder;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.media.PhotoTransformer;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class PhotoStagerTest {

	@TempDir
	Path dir;

	private static byte[] jpg() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), "jpg", out);
		return out.toByteArray();
	}

	@Test
	void 같은_게시물의_두_사진은_원본_경로가_다르다() throws Exception {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());
		PhotoStager stager = new PhotoStager(storage, new ImageDecoder(), new PhotoTransformer(), new SecureRandom());
		Post post = Post.receive(PostSource.SHARE, null, null, Clock.systemUTC());
		ReflectionTestUtils.setField(post, "id", 7L);
		String stagingId = stager.newStagingId();
		PhotoStager.Staged a = stager.stage(stagingId, 0, PhotoInput.of(jpg()), FitMode.CROP);
		PhotoStager.Staged b = stager.stage(stagingId, 1, PhotoInput.of(jpg()), FitMode.CROP);
		List<String> written = new ArrayList<>();

		// when: 같은 position 0 으로 두 번 옮긴다
		PostPhoto first = stager.promote(post, 0, a, written);
		PostPhoto second = stager.promote(post, 0, b, written);

		// then
		assertThat(first.getOriginalPath()).isNotEqualTo(second.getOriginalPath());
		assertThat(Files.exists(dir.resolve(first.getOriginalPath()))).isTrue();
		assertThat(Files.exists(dir.resolve(second.getOriginalPath()))).isTrue();
		assertThat(written).hasSize(4).contains(first.getOriginalPath(), second.getOriginalPath());
		assertThat(first.getRenderedPath()).isEqualTo("rendered/" + a.token() + ".jpg");
	}

	@Test
	void PAD로_스테이징하면_PAD로_렌더한다() throws Exception {
		// given
		PhotoStorage storage = new PhotoStorage(dir.toString());
		PhotoTransformer transformer = mock(PhotoTransformer.class);
		when(transformer.render(any(), isNull(), any(FitMode.class))).thenReturn(new byte[] { 1 });
		PhotoStager stager = new PhotoStager(storage, new ImageDecoder(), transformer, new SecureRandom());

		// when
		PhotoStager.Staged staged = stager.stage(stager.newStagingId(), 0, PhotoInput.of(jpg()), FitMode.PAD);

		// then
		verify(transformer).render(any(), isNull(), eq(FitMode.PAD));
		assertThat(staged.fitMode()).isEqualTo(FitMode.PAD);
	}

}
