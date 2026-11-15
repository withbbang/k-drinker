package com.kdrinker.analysis.label;

import java.util.List;

/** 사진(준비된 JPEG)과 메모에서 술 정보를 읽는 포트. */
public interface LabelReader {

	/** images는 입력 순서대로의 JPEG 바이트, memo는 null 가능. 결과는 sanitized(images.size())를 거쳐 쓴다. */
	LabelResult read(List<byte[]> images, String memo);
}
