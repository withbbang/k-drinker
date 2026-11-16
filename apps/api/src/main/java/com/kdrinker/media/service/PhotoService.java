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
import java.io.UncheckedIOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
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
		if (focus == null) {
			photo.changeFocus(null, null, null, null);
		} else {
			photo.changeFocus(focus.x(), focus.y(), focus.w(), focus.h());
		}
		photo.changeFitMode(mode);
		// DB 변경이 커밋된 뒤에만 공개 파일을 덮어쓴다. 롤백되면 파일은 그대로다.
		// 커밋 뒤 쓰기 실패는 던지지 않는다. 이미 커밋된 결과(예: 검토 대기 전환)를 실패로 보이게 하면 안 되기 때문이다.
		String token = photo.getPublicToken();
		Long postId = photo.getPost() == null ? null : photo.getPost().getId();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					try {
						photoStorage.saveRendered(token, rendered);
					} catch (RuntimeException e) {
						log.error("커밋 뒤 렌더링 파일을 쓰지 못했습니다. postId={} photoId={}", postId, photoId, e);
					}
				}
			});
		} else {
			photoStorage.saveRendered(token, rendered);
		}
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
				.flatMap(path -> {
					try {
						return Optional.of(photoStorage.read(path));
					} catch (UncheckedIOException e) {
						// DB에는 있으나 파일이 없거나 읽을 수 없으면 없는 것으로 본다.
						return Optional.empty();
					}
				});
	}

}
