package com.kdrinker.post.service;

import java.io.IOException;

/** 사진 한 장의 입력. 바이트는 필요할 때 읽어, 여러 장을 동시에 메모리에 올리지 않게 한다. */
public interface PhotoInput {

	/** 읽기 전에 알 수 있는 크기(바이트). */
	long size();

	byte[] readAllBytes() throws IOException;

	static PhotoInput of(byte[] bytes) {
		return new PhotoInput() {
			@Override
			public long size() {
				return bytes.length;
			}

			@Override
			public byte[] readAllBytes() {
				return bytes;
			}
		};
	}

}
