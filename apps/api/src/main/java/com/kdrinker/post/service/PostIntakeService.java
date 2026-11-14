package com.kdrinker.post.service;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.common.Tokens;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

	/** 바이트 배열 목록 버전. 텔레그램 봇처럼 이미 메모리에 있는 사진을 넘길 때 쓴다. */
	@Transactional
	public Post receive(PostSource source, String memo, BigDecimal rating, List<byte[]> photos) {
		return receiveInputs(source, memo, rating, photos.stream().map(PhotoInput::of).toList());
	}

	/**
	 * 사진을 한 장씩 읽어 디코드·렌더링한 뒤 {MEDIA_DIR}/tmp/{random}/ 에 스테이징하고 바이트는 바로 버린다.
	 * 모든 사진이 성공한 뒤에야 게시물을 저장하고 파일을 최종 경로로 옮기므로, 중간에 실패하면 아무것도 남지 않는다.
	 * 커밋이 실패해 롤백되면 옮긴 파일도 지운다.
	 */
	@Transactional
	public Post receiveInputs(PostSource source, String memo, BigDecimal rating, List<PhotoInput> photos) {
		validate(photos.size(), memo, rating);
		String stagingId = Tokens.random(secureRandom);
		List<Staged> staged = new ArrayList<>();
		List<String> written = new ArrayList<>();
		try {
			for (int i = 0; i < photos.size(); i++) {
				staged.add(stage(stagingId, i, photos.get(i)));
			}

			Post post = postRepository.save(Post.receive(source, memo, rating, clock));
			registerRollbackCleanup(written);
			for (int i = 0; i < staged.size(); i++) {
				Staged p = staged.get(i);
				String originalPath = photoStorage.promote(p.originalStaged(),
						PhotoStorage.originalPath(post.getId(), i, p.format()));
				written.add(originalPath);
				String renderedPath = photoStorage.promote(p.renderedStaged(), PhotoStorage.renderedPath(p.token()));
				written.add(renderedPath);
				PostPhoto photo = PostPhoto.of(post, i, originalPath, p.token(), FitMode.CROP);
				photo.attachRendered(renderedPath);
				postPhotoRepository.save(photo);
			}
			events.publishEvent(new PostReceivedEvent(post.getId()));
			return post;
		} catch (RuntimeException e) {
			written.forEach(photoStorage::deleteQuietly);
			throw e;
		} finally {
			photoStorage.deleteStagingQuietly(stagingId);
		}
	}

	/** 사진 한 장을 읽어 스테이징한다. 원본 바이트는 이 메서드가 끝나면 참조가 사라진다. */
	private Staged stage(String stagingId, int position, PhotoInput input) {
		if (input.size() > MAX_PHOTO_BYTES) {
			throw new BusinessException(ErrorCode.POST_PHOTO_TOO_LARGE);
		}
		byte[] original;
		try {
			original = input.readAllBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		if (original.length > MAX_PHOTO_BYTES) {
			throw new BusinessException(ErrorCode.POST_PHOTO_TOO_LARGE);
		}
		String format = imageDecoder.detectFormat(original);
		byte[] rendered = photoTransformer.render(imageDecoder.decode(original), null, FitMode.CROP);
		String originalStaged = photoStorage.saveStaged(stagingId, position + "." + format, original);
		String renderedStaged = photoStorage.saveStaged(stagingId, position + ".rendered.jpg", rendered);
		return new Staged(format, Tokens.random(secureRandom), originalStaged, renderedStaged);
	}

	/** 커밋 단계에서 실패해 롤백되면 이미 옮긴 파일을 지운다. 트랜잭션 밖이면 등록하지 않는다. */
	private void registerRollbackCleanup(List<String> written) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) {
					written.forEach(photoStorage::deleteQuietly);
				}
			}
		});
	}

	private record Staged(String format, String token, String originalStaged, String renderedStaged) {
	}

}
