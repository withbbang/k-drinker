package com.kdrinker.post.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class PostTest {

	@Test
	void 갤러리_공유로_받은_게시물은_웹_담당이다() {
		// when
		Post post = Post.receive(PostSource.SHARE, null, null, Clock.systemUTC());

		// then
		assertThat(post.getChannel()).isEqualTo(PostChannel.WEB);
	}

	@Test
	void 텔레그램으로_받은_게시물은_텔레그램_담당이다() {
		// when
		Post post = Post.receive(PostSource.TELEGRAM, null, null, Clock.systemUTC());

		// then
		assertThat(post.getChannel()).isEqualTo(PostChannel.TELEGRAM);
	}

	@Test
	void 웹으로_넘기면_웹_담당이_되고_다시_넘겨도_웹이다() {
		// given
		Post post = Post.receive(PostSource.TELEGRAM, null, null, Clock.systemUTC());

		// when
		post.moveToWeb();
		post.moveToWeb();

		// then
		assertThat(post.getChannel()).isEqualTo(PostChannel.WEB);
	}
}
