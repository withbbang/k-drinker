package com.kdrinker.post.service;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.event.PostReceivedEvent;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
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
	static final int MAX_MEMO_LENGTH = 2000;
	private static final BigDecimal MAX_RATING = new BigDecimal("5");
	private static final BigDecimal HALF = new BigDecimal("0.5");

	private final PostRepository postRepository;
	private final PostPhotoRepository postPhotoRepository;
	private final PhotoStorage photoStorage;
	private final PhotoStager photoStager;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	/** 컨트롤러와 서비스가 함께 쓰는 입력 검증. 사진 개수·별점·메모 길이를 본다. */
	public static void validate(int photoCount, String memo, BigDecimal rating) {
		if (photoCount < MIN_PHOTOS || photoCount > MAX_PHOTOS) {
			throw new BusinessException(ErrorCode.POST_PHOTO_COUNT);
		}
		validateMemoAndRating(memo, rating);
	}

	/** 메모 길이·별점 범위 검증. 사진 개수를 따로 보는 수정 저장도 쓴다. */
	public static void validateMemoAndRating(String memo, BigDecimal rating) {
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
		String stagingId = photoStager.newStagingId();
		List<PhotoStager.Staged> staged = new ArrayList<>();
		List<String> written = new ArrayList<>();
		try {
			for (int i = 0; i < photos.size(); i++) {
				staged.add(photoStager.stage(stagingId, i, photos.get(i), FitMode.CROP));
			}

			Post post = postRepository.save(Post.receive(source, memo, rating, clock));
			photoStager.registerRollbackCleanup(written);
			for (int i = 0; i < staged.size(); i++) {
				postPhotoRepository.save(photoStager.promote(post, i, staged.get(i), written));
			}
			events.publishEvent(new PostReceivedEvent(post.getId()));
			return post;
		} catch (RuntimeException e) {
			written.forEach(photoStorage::deleteQuietly);
			throw e;
		} finally {
			photoStager.deleteStaging(stagingId);
		}
	}

}
