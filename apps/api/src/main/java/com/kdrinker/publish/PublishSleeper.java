package com.kdrinker.publish;

import java.time.Duration;

/** 컨테이너 상태 조회 사이의 대기. 테스트에서 실제로 기다리지 않도록 분리했다. */
public interface PublishSleeper {

	void sleep(Duration duration) throws InterruptedException;

}
