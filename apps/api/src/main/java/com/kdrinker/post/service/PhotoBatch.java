package com.kdrinker.post.service;

import com.kdrinker.media.PhotoStorage;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import java.util.ArrayList;
import java.util.List;

/**
 * 저장 한 번에 쓰는 사진 스테이징·옮기기 묶음. 접수와 수정 저장이 같은 정리 규칙을 쓰도록 모았다.
 * <p>
 * {@link #close()}는 스테이징 폴더를 지우고, {@link #keepPromoted()}를 부르지 않았으면 옮긴 파일도 지운다.
 * 쓰기 본문이 끝난 뒤의 예외(커밋 실패·커밋 뒤 콜백)에서는 옮긴 파일을 여기서 지우지 않는다. 롤백되면
 * {@link #registerRollbackCleanup()}이 등록한 콜백이 지우고, 커밋됐으면 파일은 새 사진 행이 쓴다.
 * 빈은 아니다. 호출 쪽이 저장마다 새로 만든다.
 */
final class PhotoBatch implements AutoCloseable {

	private final PhotoStager stager;
	private final PhotoStorage storage;
	private final List<String> written = new ArrayList<>();
	private String stagingId;
	private boolean keep;

	PhotoBatch(PhotoStager stager, PhotoStorage storage) {
		this.stager = stager;
		this.storage = storage;
	}

	/** 사진 한 장을 스테이징한다. 처음 부를 때 스테이징 폴더 id를 만든다. */
	PhotoStager.Staged stage(int index, PhotoInput input, FitMode fitMode) {
		if (stagingId == null) {
			stagingId = stager.newStagingId();
		}
		return stager.stage(stagingId, index, input, fitMode);
	}

	/** 쓰기 트랜잭션 안에서 부른다. 커밋 단계에서 롤백되면 옮긴 파일을 지운다. */
	void registerRollbackCleanup() {
		stager.registerRollbackCleanup(written);
	}

	PostPhoto promote(Post post, int position, PhotoStager.Staged staged) {
		return stager.promote(post, position, staged, written);
	}

	/** 쓰기 본문이 끝났다. 이 뒤의 예외로는 옮긴 파일을 지우지 않는다. */
	void keepPromoted() {
		keep = true;
	}

	@Override
	public void close() {
		try {
			if (!keep) {
				written.forEach(storage::deleteQuietly);
			}
		} finally {
			if (stagingId != null) {
				stager.deleteStaging(stagingId);
			}
		}
	}
}
