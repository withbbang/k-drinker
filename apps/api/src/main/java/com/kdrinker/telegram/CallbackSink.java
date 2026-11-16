package com.kdrinker.telegram;

import com.kdrinker.telegram.dto.CallbackQuery;

/** 소유자 chat의 버튼 콜백을 받는 곳. 구현이 없으면 폴러가 로그만 남긴다. */
public interface CallbackSink {

	void handle(CallbackQuery query);

}
