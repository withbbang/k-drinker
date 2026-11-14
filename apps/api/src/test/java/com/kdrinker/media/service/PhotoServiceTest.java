package com.kdrinker.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

	@Test
	void 공개_URL은_기준_주소와_토큰으로_만든다() {
		// given
		PostPhoto photo = PostPhoto.of(null, 0, "original/1/0.jpg", TOKEN, FitMode.CROP);

		// when / then
		assertThat(service.publicUrl(photo)).isEqualTo("https://example.com/api/v1/media/" + TOKEN + ".jpg");
	}

}
