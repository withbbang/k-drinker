package com.kdrinker.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.media.ImageDecoder;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.media.PhotoTransformer;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.event.PostReceivedEvent;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class PostIntakeServiceTest {

	private final PostRepository postRepository = mock(PostRepository.class);
	private final PostPhotoRepository postPhotoRepository = mock(PostPhotoRepository.class);
	private final PhotoStorage photoStorage = mock(PhotoStorage.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final SecureRandom fixedRandom = new SecureRandom() {
		@Override
		public void nextBytes(byte[] bytes) {
			Arrays.fill(bytes, (byte) 7);
		}
	};
	private PostIntakeService service;

	@BeforeEach
	void setUp() {
		service = new PostIntakeService(postRepository, postPhotoRepository, photoStorage, new ImageDecoder(),
				new PhotoTransformer(), fixedRandom, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), events);
		when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
			Post post = inv.getArgument(0);
			ReflectionTestUtils.setField(post, "id", 5L);
			return post;
		});
		when(photoStorage.saveOriginal(anyLong(), anyInt(), anyString(), any())).thenAnswer(
				inv -> "original/" + inv.getArgument(0) + "/" + inv.getArgument(1) + "." + inv.getArgument(2));
		when(photoStorage.saveRendered(anyString(), any())).thenAnswer(inv -> "rendered/" + inv.getArgument(0) + ".jpg");
	}

	private static byte[] image(String format) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), format, out);
		return out.toByteArray();
	}

	@Test
	void 하나라도_디코드에_실패하면_아무것도_저장하지_않는다() throws Exception {
		// given
		List<byte[]> photos = List.of(image("jpg"), "텍스트".getBytes(StandardCharsets.UTF_8));

		// when / then
		assertThatThrownBy(() -> service.receive(PostSource.SHARE, null, null, photos))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEDIA_UNSUPPORTED_TYPE));
		verify(postRepository, never()).save(any());
		verifyNoInteractions(photoStorage, postPhotoRepository, events);
	}

	@Test
	void 접수하면_원본과_렌더링을_저장하고_PostReceivedEvent를_발행한다() throws Exception {
		// given
		List<byte[]> photos = List.of(image("jpg"), image("png"));

		// when
		Post post = service.receive(PostSource.SHARE, "메모", new BigDecimal("4.5"), photos);

		// then
		assertThat(post.getId()).isEqualTo(5L);
		verify(photoStorage).saveOriginal(eq(5L), eq(0), eq("jpg"), any());
		verify(photoStorage).saveOriginal(eq(5L), eq(1), eq("png"), any());
		ArgumentCaptor<PostPhoto> captor = ArgumentCaptor.forClass(PostPhoto.class);
		verify(postPhotoRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		PostPhoto first = captor.getAllValues().get(0);
		assertThat(first.getPublicToken()).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
		assertThat(first.getFitMode()).isEqualTo(FitMode.CROP);
		assertThat(first.getRenderedPath()).isEqualTo("rendered/" + first.getPublicToken() + ".jpg");
		assertThat(first.getOriginalPath()).isEqualTo("original/5/0.jpg");
		verify(events).publishEvent(new PostReceivedEvent(5L));
	}

	@Test
	void 사진이_20MB를_넘으면_POST_PHOTO_TOO_LARGE다() {
		// given
		List<byte[]> photos = List.of(new byte[20 * 1024 * 1024 + 1]);

		// when / then
		assertThatThrownBy(() -> service.receive(PostSource.TELEGRAM, null, null, photos))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POST_PHOTO_TOO_LARGE));
		verify(postRepository, never()).save(any());
	}

}
