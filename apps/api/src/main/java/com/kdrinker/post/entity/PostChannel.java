package com.kdrinker.post.entity;

/**
 * 게시물을 맡은 화면. 텔레그램 담당이면 텔레그램 개요·버튼으로 다루고, 웹 담당이면 텔레그램 알림·버튼을 막는다.
 * {@code TELEGRAM → WEB} 한 방향으로만 바뀐다.
 */
public enum PostChannel {
	TELEGRAM, WEB
}
