package com.kdrinker.post.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdrinker.media.PhotoStorage;
import com.kdrinker.post.entity.FitMode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhotoBatchTest {

	private final PhotoStager stager = mock(PhotoStager.class);
	private final PhotoStorage storage = mock(PhotoStorage.class);
	private final PhotoStager.Staged staged = new PhotoStager.Staged("jpg", "t", FitMode.CROP, "tmp/s/0.jpg",
			"tmp/s/0.rendered.jpg");

	@BeforeEach
	void setUp() {
		when(stager.newStagingId()).thenReturn("s");
		when(stager.promote(any(), anyInt(), eq(staged), any())).thenAnswer(inv -> {
			List<String> written = inv.getArgument(3);
			written.add("original/1/a.jpg");
			written.add("rendered/t.jpg");
			return null;
		});
	}

	@Test
	void 본문을_끝내지_못하면_옮긴_파일과_스테이징을_지운다() {
		// given · when
		try (PhotoBatch batch = new PhotoBatch(stager, storage)) {
			batch.stage(0, PhotoInput.of(new byte[] { 1 }), FitMode.CROP);
			batch.promote(null, 0, staged);
		}

		// then
		verify(storage).deleteQuietly("original/1/a.jpg");
		verify(storage).deleteQuietly("rendered/t.jpg");
		verify(stager).deleteStaging("s");
	}

	@Test
	void 본문을_끝냈으면_옮긴_파일은_남기고_스테이징만_지운다() {
		// given · when
		try (PhotoBatch batch = new PhotoBatch(stager, storage)) {
			batch.stage(0, PhotoInput.of(new byte[] { 1 }), FitMode.CROP);
			batch.promote(null, 0, staged);
			batch.keepPromoted();
		}

		// then
		verify(storage, never()).deleteQuietly(anyString());
		verify(stager).deleteStaging("s");
	}

	@Test
	void 스테이징_id는_여러_장이어도_한_번만_만든다() {
		// given · when
		try (PhotoBatch batch = new PhotoBatch(stager, storage)) {
			batch.stage(0, PhotoInput.of(new byte[] { 1 }), FitMode.CROP);
			batch.stage(1, PhotoInput.of(new byte[] { 2 }), FitMode.PAD);
		}

		// then
		verify(stager, times(1)).newStagingId();
		verify(stager).stage(eq("s"), eq(1), any(), eq(FitMode.PAD));
	}

	@Test
	void 스테이징하지_않았으면_스테이징_폴더를_지우지_않는다() {
		// given · when
		try (PhotoBatch batch = new PhotoBatch(stager, storage)) {
			batch.keepPromoted();
		}

		// then
		verify(stager, never()).newStagingId();
		verify(stager, never()).deleteStaging(anyString());
	}
}
