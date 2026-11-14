package com.kdrinker.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
		when(photoStorage.saveStaged(anyString(), anyString(), any()))
				.thenAnswer(inv -> "tmp/" + inv.getArgument(0) + "/" + inv.getArgument(1));
		when(photoStorage.promote(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
	}

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
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
		verify(photoStorage, never()).promote(anyString(), anyString());
		verify(photoStorage).deleteStagingQuietly(anyString());
		verifyNoInteractions(postPhotoRepository, events);
	}

	@Test
	void 접수하면_원본과_렌더링을_저장하고_PostReceivedEvent를_발행한다() throws Exception {
		// given
		List<byte[]> photos = List.of(image("jpg"), image("png"));

		// when
		Post post = service.receive(PostSource.SHARE, "메모", new BigDecimal("4.5"), photos);

		// then
		assertThat(post.getId()).isEqualTo(5L);
		verify(photoStorage).promote(anyString(), eq("original/5/0.jpg"));
		verify(photoStorage).promote(anyString(), eq("original/5/1.png"));
		ArgumentCaptor<PostPhoto> captor = ArgumentCaptor.forClass(PostPhoto.class);
		verify(postPhotoRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		PostPhoto first = captor.getAllValues().get(0);
		assertThat(first.getPublicToken()).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
		assertThat(first.getFitMode()).isEqualTo(FitMode.CROP);
		assertThat(first.getRenderedPath()).isEqualTo("rendered/" + first.getPublicToken() + ".jpg");
		assertThat(first.getOriginalPath()).isEqualTo("original/5/0.jpg");
		verify(photoStorage, org.mockito.Mockito.atLeastOnce()).promote(anyString(), eq(first.getRenderedPath())); // 고정 난수라 두 장의 토큰이 같다
		verify(events).publishEvent(new PostReceivedEvent(5L));
		verify(photoStorage).deleteStagingQuietly(anyString());
	}

	private PhotoInput mockInput(byte[] bytes) throws Exception {
		PhotoInput input = mock(PhotoInput.class);
		when(input.size()).thenReturn((long) bytes.length);
		when(input.readAllBytes()).thenReturn(bytes);
		return input;
	}

	@Test
	void 사진은_한_장씩_읽어_스테이징한_뒤_다음_장을_읽는다() throws Exception {
		// given
		PhotoInput first = mockInput(image("jpg"));
		PhotoInput second = mockInput(image("png"));

		// when
		service.receiveInputs(PostSource.SHARE, null, null, List.of(first, second));

		// then
		InOrder order = inOrder(first, second, photoStorage);
		order.verify(first).readAllBytes();
		order.verify(photoStorage).saveStaged(anyString(), eq("0.jpg"), any());
		order.verify(photoStorage).saveStaged(anyString(), eq("0.rendered.jpg"), any());
		order.verify(second).readAllBytes();
		order.verify(photoStorage).saveStaged(anyString(), eq("1.png"), any());
	}

	@Test
	void 크기가_한도를_넘는_입력은_읽지_않고_거절한다() throws Exception {
		// given
		PhotoInput huge = mock(PhotoInput.class);
		when(huge.size()).thenReturn(20L * 1024 * 1024 + 1);

		// when / then
		assertThatThrownBy(() -> service.receiveInputs(PostSource.SHARE, null, null, List.of(huge)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POST_PHOTO_TOO_LARGE));
		verify(huge, never()).readAllBytes();
	}

	@Test
	void 입력을_읽다_실패하면_검증_실패이고_스테이징을_지운다() throws Exception {
		// given
		PhotoInput broken = mock(PhotoInput.class);
		when(broken.size()).thenReturn(10L);
		when(broken.readAllBytes()).thenThrow(new java.io.IOException("끊김"));

		// when / then
		assertThatThrownBy(() -> service.receiveInputs(PostSource.SHARE, null, null, List.of(broken)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
		verify(photoStorage).deleteStagingQuietly(anyString());
		verify(postRepository, never()).save(any());
	}

	@Test
	void 트랜잭션이_롤백되면_이미_옮긴_파일을_지운다() throws Exception {
		// given
		TransactionSynchronizationManager.initSynchronization();
		Post post = service.receive(PostSource.SHARE, null, null, List.of(image("jpg")));
		ArgumentCaptor<PostPhoto> captor = ArgumentCaptor.forClass(PostPhoto.class);
		verify(postPhotoRepository).save(captor.capture());

		// when
		for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
			sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		}

		// then
		assertThat(post.getId()).isEqualTo(5L);
		verify(photoStorage).deleteQuietly("original/5/0.jpg");
		verify(photoStorage).deleteQuietly(captor.getValue().getRenderedPath());
	}

	@Test
	void 트랜잭션이_커밋되면_파일을_지우지_않는다() throws Exception {
		// given
		TransactionSynchronizationManager.initSynchronization();
		service.receive(PostSource.SHARE, null, null, List.of(image("jpg")));

		// when
		for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
			sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		}

		// then
		verify(photoStorage, never()).deleteQuietly(anyString());
	}

	@Test
	void 파일을_옮긴_뒤_저장이_실패하면_옮긴_파일을_지운다() throws Exception {
		// given
		when(postPhotoRepository.save(any(PostPhoto.class))).thenThrow(new IllegalStateException("db"));

		// when / then
		assertThatThrownBy(() -> service.receive(PostSource.SHARE, null, null, List.of(image("jpg"))))
				.isInstanceOf(IllegalStateException.class);
		verify(photoStorage).deleteQuietly("original/5/0.jpg");
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
