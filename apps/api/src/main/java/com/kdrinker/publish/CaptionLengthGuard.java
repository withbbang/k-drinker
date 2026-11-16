package com.kdrinker.publish;

/** 캡션이 인스타그램 한도를 넘는지 확인한다. 넘으면 게시물을 실패 처리하고 true를 돌려준다. */
public interface CaptionLengthGuard {

	boolean failIfTooLong(Long postId);

}
