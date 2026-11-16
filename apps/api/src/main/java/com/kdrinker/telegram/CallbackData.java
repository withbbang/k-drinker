package com.kdrinker.telegram;

import java.util.Optional;
import java.util.Set;

/** 인라인 버튼의 콜백 데이터 {@code act:{postId}:{version}}. 텔레그램은 64바이트까지 허용한다. */
public record CallbackData(String act, long postId, int version) {

	static final Set<String> ACTS = Set.of("ok", "detail", "edit", "cancel", "retry");

	/** 형식이 다르거나 act가 다섯 개 밖이면 비어 있다. */
	public static Optional<CallbackData> parse(String data) {
		if (data == null) {
			return Optional.empty();
		}
		String[] parts = data.split(":", -1);
		if (parts.length != 3 || !ACTS.contains(parts[0])) {
			return Optional.empty();
		}
		try {
			return Optional.of(new CallbackData(parts[0], Long.parseLong(parts[1]), Integer.parseInt(parts[2])));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

}
