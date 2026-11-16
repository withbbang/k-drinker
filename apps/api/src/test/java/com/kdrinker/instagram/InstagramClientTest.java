package com.kdrinker.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kdrinker.publish.PublishFailure;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(value = InstagramClient.class,
		properties = { "app.instagram.base-url=https://graph.instagram.com", "app.instagram.api-version=v27.0",
				"app.instagram.user-id=1789" })
class InstagramClientTest {

	private static final String TOKEN = "IGAAsecret-token_123";
	private static final String BASE = "https://graph.instagram.com/v27.0/";

	@Autowired
	private InstagramClient client;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void 캐러셀_요청_모양이_맞다() {
		// given 자식 컨테이너
		server.expect(requestTo(BASE + "1789/media"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formDataContains(Map.of("image_url", "https://x/1.jpg", "is_carousel_item", "true",
					"access_token", TOKEN)))
			.andRespond(withSuccess("{\"id\":\"c1\"}", MediaType.APPLICATION_JSON));
		// given 부모 컨테이너
		server.expect(requestTo(BASE + "1789/media"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formDataContains(Map.of("media_type", "CAROUSEL", "children", "c1,c2", "caption",
					"본문 #태그")))
			.andRespond(withSuccess("{\"id\":\"p1\"}", MediaType.APPLICATION_JSON));

		// when
		String child = client.createImageContainer(TOKEN, "https://x/1.jpg", true, null);
		String parent = client.createCarouselContainer(TOKEN, List.of("c1", "c2"), "본문 #태그");

		// then
		assertThat(child).isEqualTo("c1");
		assertThat(parent).isEqualTo("p1");
		server.verify();
	}

	@Test
	void 단일_이미지는_캡션을_함께_보낸다() {
		// given
		server.expect(requestTo(BASE + "1789/media"))
			.andExpect(content().formDataContains(Map.of("image_url", "https://x/1.jpg", "caption", "한 장")))
			.andRespond(withSuccess("{\"id\":\"s1\"}", MediaType.APPLICATION_JSON));

		// when
		String id = client.createImageContainer(TOKEN, "https://x/1.jpg", false, "한 장");

		// then
		assertThat(id).isEqualTo("s1");
	}

	@Test
	void status_code를_읽는다() {
		// given
		server.expect(requestTo(BASE + "c1?fields=status_code&access_token=" + TOKEN))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"status_code\":\"FINISHED\",\"id\":\"c1\"}", MediaType.APPLICATION_JSON));

		// when
		InstagramClient.ContainerStatus status = client.containerStatus(TOKEN, "c1");

		// then
		assertThat(status).isEqualTo(InstagramClient.ContainerStatus.FINISHED);
	}

	@Test
	void 게시하고_permalink를_읽는다() {
		// given
		server.expect(requestTo(BASE + "1789/media_publish"))
			.andExpect(content().formDataContains(Map.of("creation_id", "p1")))
			.andRespond(withSuccess("{\"id\":\"m1\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "m1?fields=permalink&access_token=" + TOKEN))
			.andRespond(withSuccess("{\"permalink\":\"https://www.instagram.com/p/abc/\",\"id\":\"m1\"}",
					MediaType.APPLICATION_JSON));

		// when
		String mediaId = client.publish(TOKEN, "p1");

		// then
		assertThat(mediaId).isEqualTo("m1");
		assertThat(client.permalink(TOKEN, "m1")).contains("https://www.instagram.com/p/abc/");
	}

	@Test
	void permalink_조회가_실패하면_비어_있다() {
		// given
		server.expect(requestTo(BASE + "m1?fields=permalink&access_token=" + TOKEN))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		// when / then
		assertThat(client.permalink(TOKEN, "m1")).isEmpty();
	}

	@Test
	void 에러_본문은_예외가_되고_190은_AUTH다() {
		// given
		server.expect(requestTo(BASE + "1789/media"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"message\":\"Invalid OAuth access token.\",\"type\":\"OAuthException\",\"code\":190}}"));

		// when / then
		assertThatThrownBy(() -> client.createImageContainer(TOKEN, "https://x/1.jpg", false, "c"))
			.isInstanceOfSatisfying(InstagramApiException.class, e -> {
				assertThat(e.getCode()).isEqualTo(190);
				assertThat(e.classify()).isEqualTo(PublishFailure.AUTH);
			});
	}

	@Test
	void 하위코드_2207042는_RATE_LIMIT이다() {
		// given
		server.expect(requestTo(BASE + "1789/media_publish"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"message\":\"limit\",\"code\":9,\"error_subcode\":2207042}}"));

		// when / then
		assertThatThrownBy(() -> client.publish(TOKEN, "p1"))
			.isInstanceOfSatisfying(InstagramApiException.class, e -> {
				assertThat(e.getSubcode()).isEqualTo(2207042);
				assertThat(e.classify()).isEqualTo(PublishFailure.RATE_LIMIT);
			});
	}

	@Test
	void 분류표가_맞다() {
		assertThat(new InstagramApiException(10, null, "m").classify()).isEqualTo(PublishFailure.AUTH);
		assertThat(new InstagramApiException(250, null, "m").classify()).isEqualTo(PublishFailure.AUTH);
		assertThat(new InstagramApiException(4, null, "m").classify()).isEqualTo(PublishFailure.RATE_LIMIT);
		assertThat(new InstagramApiException(17, null, "m").classify()).isEqualTo(PublishFailure.RATE_LIMIT);
		assertThat(new InstagramApiException(9004, 2207052, "m").classify()).isEqualTo(PublishFailure.IMAGE_FETCH);
		assertThat(new InstagramApiException(-2, 2207003, "m").classify()).isEqualTo(PublishFailure.IMAGE_FETCH);
		assertThat(new InstagramApiException(1, null, "m").classify()).isEqualTo(PublishFailure.UNKNOWN);
	}

	@Test
	void refresh는_expires_in을_읽는다() {
		// given
		server.expect(requestTo(
				"https://graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token&access_token=" + TOKEN))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"access_token\":\"NEW\",\"token_type\":\"bearer\",\"expires_in\":5184000}",
					MediaType.APPLICATION_JSON));

		// when
		InstagramClient.RefreshedToken refreshed = client.refresh(TOKEN);

		// then
		assertThat(refreshed.accessToken()).isEqualTo("NEW");
		assertThat(refreshed.expiresInSeconds()).isEqualTo(5_184_000L);
	}

	@Test
	void 예외_메시지에_토큰이_없다() {
		// given 서버가 토큰을 본문에 되돌려 줘도, 연결 실패여도
		server.expect(requestTo(BASE + "c1?fields=status_code&access_token=" + TOKEN))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"message\":\"bad token " + TOKEN + "\",\"code\":190}}"));
		server.expect(requestTo(BASE + "c2?fields=status_code&access_token=" + TOKEN))
			.andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>" + TOKEN + "</html>"));

		// when / then
		assertThatThrownBy(() -> client.containerStatus(TOKEN, "c1"))
			.isInstanceOf(InstagramApiException.class)
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain(TOKEN));
		assertThatThrownBy(() -> client.containerStatus(TOKEN, "c2"))
			.isInstanceOf(InstagramApiException.class)
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain(TOKEN));
	}

}
