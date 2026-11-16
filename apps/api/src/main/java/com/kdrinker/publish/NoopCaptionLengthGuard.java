package com.kdrinker.publish;

import org.springframework.stereotype.Component;

/** 임시 구현. Task 6에서 PublishService가 CaptionLengthGuard를 구현하면 이 클래스를 지운다. */
@Component
public class NoopCaptionLengthGuard implements CaptionLengthGuard {

	@Override
	public boolean failIfTooLong(Long postId) {
		return false;
	}

}
