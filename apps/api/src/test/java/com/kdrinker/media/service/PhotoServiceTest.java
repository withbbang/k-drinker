package com.kdrinker.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kdrinker.media.ImageDecoder;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.media.PhotoTransformer;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.repository.PostPhotoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(OutputCaptureExtension.class)
class PhotoServiceTest {

	private static final String TOKEN = "B".repeat(43);

	private final PostPhotoRepository repository = mock(PostPhotoRepository.class);
	private final PhotoStorage storage = mock(PhotoStorage.class);
	private final PhotoService service = new PhotoService(repository, storage, new ImageDecoder(),
			new PhotoTransformer(), "https://example.com/");

	@Test
	void 형식이_틀린_토큰은_DB를_조회하지_않는다() {
		// when
		Optional<byte[]> result = service.readRendered("../../etc/passwd");

		// then
		assertThat(result).isEmpty();
		verifyNoInteractions(repository, storage);
	}

	@Test
	void 형식이_틀린_토큰_여러_종류는_모두_비어_있고_DB를_조회하지_않는다() {
		// when / then
		assertThat(service.readRendered("abc")).isEmpty();
		assertThat(service.readRendered("a".repeat(42))).isEmpty();
		assertThat(service.readRendered("..%2F..%2Fetc%2Fpasswd")).isEmpty();
		assertThat(service.readRendered("a".repeat(44))).isEmpty();
		verifyNoInteractions(repository, storage);
	}

	@Test
	void DB에는_있지만_파일이_없으면_비어_있다() {
		// given
		PostPhoto photo = PostPhoto.of(null, 0, "original/1/0.jpg", TOKEN, FitMode.CROP);
		photo.attachRendered("rendered/" + TOKEN + ".jpg");
		when(repository.findByPublicToken(TOKEN)).thenReturn(Optional.of(photo));
		when(storage.read("rendered/" + TOKEN + ".jpg"))
				.thenThrow(new java.io.UncheckedIOException(new java.nio.file.NoSuchFileException("x")));

		// when / then
		assertThat(service.readRendered(TOKEN)).isEmpty();
	}

	@Test
	void DB에_있는_토큰이면_렌더링_파일을_읽는다() {
		// given
		PostPhoto photo = PostPhoto.of(null, 0, "original/1/0.jpg", TOKEN, FitMode.CROP);
		photo.attachRendered("rendered/" + TOKEN + ".jpg");
		when(repository.findByPublicToken(TOKEN)).thenReturn(Optional.of(photo));
		when(storage.read("rendered/" + TOKEN + ".jpg")).thenReturn(new byte[] { 9 });

		// when
		Optional<byte[]> result = service.readRendered(TOKEN);

		// then
		assertThat(result).hasValueSatisfying(b -> assertThat(b).containsExactly(9));
	}

	private PostPhoto photoWithOriginal() throws Exception {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(60, 40, java.awt.image.BufferedImage.TYPE_INT_RGB),
				"jpg", out);
		PostPhoto photo = PostPhoto.of(null, 0, "original/1/0.jpg", TOKEN, FitMode.CROP);
		photo.attachRendered("rendered/" + TOKEN + ".jpg");
		when(repository.findById(1L)).thenReturn(Optional.of(photo));
		when(storage.read("original/1/0.jpg")).thenReturn(out.toByteArray());
		return photo;
	}

	@Test
	void 재렌더링은_커밋_뒤에_파일을_덮어쓴다() throws Exception {
		// given
		photoWithOriginal();
		TransactionSynchronizationManager.initSynchronization();

		try {
			// when
			service.rerender(1L, null, FitMode.PAD);

			// then 커밋 전에는 공개 파일을 건드리지 않는다
			verify(storage, never()).saveRendered(any(), any());
			for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
				sync.afterCommit();
			}
			verify(storage).saveRendered(eq(TOKEN), any());
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void 재렌더링_트랜잭션이_롤백되면_파일은_그대로다() throws Exception {
		// given
		photoWithOriginal();
		TransactionSynchronizationManager.initSynchronization();

		try {
			// when
			service.rerender(1L, null, FitMode.PAD);
			for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
				sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
			}

			// then
			verify(storage, never()).saveRendered(any(), any());
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void 트랜잭션이_없으면_재렌더링_파일을_바로_쓴다() throws Exception {
		// given
		photoWithOriginal();

		// when
		service.rerender(1L, null, FitMode.PAD);

		// then
		verify(storage).saveRendered(eq(TOKEN), any());
	}

	@Test
	void 공개_URL은_기준_주소와_토큰으로_만든다() {
		// given
		PostPhoto photo = PostPhoto.of(null, 0, "original/1/0.jpg", TOKEN, FitMode.CROP);

		// when / then
		assertThat(service.publicUrl(photo)).isEqualTo("https://example.com/api/v1/media/" + TOKEN + ".jpg");
	}

	@Test
	void 커밋_뒤_파일_쓰기가_실패해도_예외를_던지지_않고_ERROR_로그를_남긴다(CapturedOutput output) throws Exception {
		// given
		photoWithOriginal();
		doThrow(new java.io.UncheckedIOException(new java.io.IOException("디스크 가득 참"))).when(storage)
				.saveRendered(eq(TOKEN), any());
		TransactionSynchronizationManager.initSynchronization();

		try {
			// when
			service.rerender(1L, null, FitMode.PAD);
			for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
				sync.afterCommit();
			}

			// then
			assertThat(output).contains("ERROR").contains("photoId=1").contains("postId=");
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

}
