package com.kdrinker.media.service;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.media.FocusBox;
import com.kdrinker.media.ImageDecoder;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.media.PhotoTransformer;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.repository.PostPhotoRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PhotoService {

	private final PostPhotoRepository postPhotoRepository;
	private final PhotoStorage photoStorage;
	private final ImageDecoder imageDecoder;
	private final PhotoTransformer photoTransformer;
	private final String publicBaseUrl;

	public PhotoService(PostPhotoRepository postPhotoRepository, PhotoStorage photoStorage,
			ImageDecoder imageDecoder, PhotoTransformer photoTransformer,
			@Value("${app.public-base-url}") String publicBaseUrl) {
		this.postPhotoRepository = postPhotoRepository;
		this.photoStorage = photoStorage;
		this.imageDecoder = imageDecoder;
		this.photoTransformer = photoTransformer;
		this.publicBaseUrl = publicBaseUrl.endsWith("/")
				? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
	}

	/** 원본에서 다시 렌더링한다. 공개 토큰은 그대로 두고 렌더링 파일만 덮어쓴다. */
	@Transactional
	public void rerender(Long photoId, FocusBox focus, FitMode mode) {
		PostPhoto photo = postPhotoRepository.findById(photoId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		byte[] original = photoStorage.read(photo.getOriginalPath());
		byte[] rendered = photoTransformer.render(imageDecoder.decode(original), focus, mode);
		photoStorage.saveRendered(photo.getPublicToken(), rendered);
		if (focus == null) {
			photo.changeFocus(null, null, null, null);
		} else {
			photo.changeFocus(focus.x(), focus.y(), focus.w(), focus.h());
		}
		photo.changeFitMode(mode);
	}

	public String publicUrl(PostPhoto photo) {
		return publicBaseUrl + "/api/v1/media/" + photo.getPublicToken() + ".jpg";
	}

	/** 토큰이 형식에 맞고 DB에 있을 때만 렌더링 파일 바이트를 돌려준다. 원본은 다루지 않는다. */
	public Optional<byte[]> readRendered(String token) {
		if (token == null || !PhotoStorage.TOKEN_PATTERN.matcher(token).matches()) {
			return Optional.empty();
		}
		return postPhotoRepository.findByPublicToken(token)
				.map(PostPhoto::getRenderedPath)
				.map(photoStorage::read);
	}

}
