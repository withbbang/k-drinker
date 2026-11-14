package com.kdrinker.post.service;

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
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostIntakeService {

	static final int MIN_PHOTOS = 1;
	static final int MAX_PHOTOS = 10;
	static final int MAX_PHOTO_BYTES = 20 * 1024 * 1024;
	static final int MAX_MEMO_LENGTH = 2000;
	private static final BigDecimal MAX_RATING = new BigDecimal("5");
	private static final BigDecimal HALF = new BigDecimal("0.5");

	private final PostRepository postRepository;
	private final PostPhotoRepository postPhotoRepository;
	private final PhotoStorage photoStorage;
	private final ImageDecoder imageDecoder;
	private final PhotoTransformer photoTransformer;
	private final SecureRandom secureRandom;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	/** 컨트롤러와 서비스가 함께 쓰는 입력 검증. 사진 개수·별점·메모 길이를 본다. */
	public static void validate(int photoCount, String memo, BigDecimal rating) {
		if (photoCount < MIN_PHOTOS || photoCount > MAX_PHOTOS) {
			throw new BusinessException(ErrorCode.POST_PHOTO_COUNT);
		}
		if (memo != null && memo.length() > MAX_MEMO_LENGTH) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		if (rating != null && (rating.signum() < 0 || rating.compareTo(MAX_RATING) > 0
				|| rating.remainder(HALF).signum() != 0)) {
			throw new BusinessException(ErrorCode.POST_INVALID_RATING);
		}
	}

	/**
	 * 사진을 한 장씩 디코드해 CROP·focus 없음으로 렌더링한다. 디코드한 이미지는 바로 버려 한 번에 한 장만 메모리에 둔다.
	 * 모든 사진이 성공한 뒤에야 게시물과 파일을 저장하므로, 중간에 실패하면 아무것도 남지 않는다.
	 */
	@Transactional
	public Post receive(PostSource source, String memo, BigDecimal rating, List<byte[]> photos) {
		validate(photos.size(), memo, rating);
		List<Prepared> prepared = new ArrayList<>();
		for (byte[] original : photos) {
			if (original.length > MAX_PHOTO_BYTES) {
				throw new BusinessException(ErrorCode.POST_PHOTO_TOO_LARGE);
			}
			String format = imageDecoder.detectFormat(original);
			BufferedImage image = imageDecoder.decode(original);
			byte[] rendered = photoTransformer.render(image, null, FitMode.CROP);
			prepared.add(new Prepared(original, format, rendered));
		}

		Post post = postRepository.save(Post.receive(source, memo, rating, clock));
		List<String> written = new ArrayList<>();
		try {
			for (int i = 0; i < prepared.size(); i++) {
				Prepared p = prepared.get(i);
				String token = newToken();
				String originalPath = photoStorage.saveOriginal(post.getId(), i, p.format(), p.original());
				written.add(originalPath);
				String renderedPath = photoStorage.saveRendered(token, p.rendered());
				written.add(renderedPath);
				PostPhoto photo = PostPhoto.of(post, i, originalPath, token, FitMode.CROP);
				photo.attachRendered(renderedPath);
				postPhotoRepository.save(photo);
			}
			events.publishEvent(new PostReceivedEvent(post.getId()));
		} catch (RuntimeException e) {
			written.forEach(photoStorage::deleteQuietly);
			throw e;
		}
		return post;
	}

	private String newToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private record Prepared(byte[] original, String format, byte[] rendered) {
	}

}
