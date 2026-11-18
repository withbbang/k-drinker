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
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 사진을 읽어 디코드·렌더링해 스테이징하고, 최종 경로로 옮기는 부품. 접수와 수정 저장이 함께 쓴다.
 * 스테이징은 트랜잭션 밖에서, 옮기기는 쓰기 트랜잭션 안에서 한다.
 */
@Component
@RequiredArgsConstructor
public class PhotoStager {

	static final int MAX_PHOTO_BYTES = 20 * 1024 * 1024;

	private final PhotoStorage photoStorage;
	private final ImageDecoder imageDecoder;
	private final PhotoTransformer photoTransformer;
	private final SecureRandom secureRandom;

	public String newStagingId() {
		return Tokens.random(secureRandom);
	}

	/** 사진 한 장을 읽어 스테이징한다. 원본 바이트는 이 메서드가 끝나면 참조가 사라진다. */
	public Staged stage(String stagingId, int index, PhotoInput input, FitMode fitMode) {
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
		byte[] rendered = photoTransformer.render(imageDecoder.decode(original), null, fitMode);
		String originalStaged = photoStorage.saveStaged(stagingId, index + "." + format, original);
		String renderedStaged = photoStorage.saveStaged(stagingId, index + ".rendered.jpg", rendered);
		return new Staged(format, Tokens.random(secureRandom), fitMode, originalStaged, renderedStaged);
	}

	/** 스테이징 파일을 최종 경로(원본은 고유 이름)로 옮기고 사진 행을 만든다. 저장은 호출 쪽이 한다. */
	public PostPhoto promote(Post post, int position, Staged staged, List<String> written) {
		String originalPath = photoStorage.promote(staged.originalStaged(),
				PhotoStorage.originalPath(post.getId(), Tokens.random(secureRandom), staged.format()));
		written.add(originalPath);
		String renderedPath = photoStorage.promote(staged.renderedStaged(), PhotoStorage.renderedPath(staged.token()));
		written.add(renderedPath);
		PostPhoto photo = PostPhoto.of(post, position, originalPath, staged.token(), staged.fitMode());
		photo.attachRendered(renderedPath);
		return photo;
	}

	/** 커밋 단계에서 실패해 롤백되면 이미 옮긴 파일을 지운다. 트랜잭션 밖이면 등록하지 않는다. */
	public void registerRollbackCleanup(List<String> written) {
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

	public void deleteStaging(String stagingId) {
		photoStorage.deleteStagingQuietly(stagingId);
	}

	public record Staged(String format, String token, FitMode fitMode, String originalStaged,
			String renderedStaged) {
	}

}
