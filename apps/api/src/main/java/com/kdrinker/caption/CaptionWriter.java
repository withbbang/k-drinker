package com.kdrinker.caption;

/** 캡션 문장·태그 생성 포트. */
public interface CaptionWriter {

	CaptionDraft write(CaptionInput in);

	/** 이전 초안이 currentLength자(마커 제거·해시태그 포함)로 상한을 넘었을 때 줄인 초안을 받는다. */
	CaptionDraft shorten(CaptionInput in, CaptionDraft previous, int currentLength);
}
