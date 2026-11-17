package com.kdrinker.instagram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Instagram API with Facebook Login(통합 로그인, {@code graph.facebook.com}) 클라이언트. 토큰은 만료되지 않는
 * 시스템 사용자 토큰이라 갱신 호출은 없다. 모든 실패는 토큰이 없는 {@link InstagramApiException}으로 알린다.
 * 토큰은 GET에서는 쿼리, POST에서는 폼 본문으로 보내고 예외 메시지에는 넣지 않는다.
 */
@Component
@EnableConfigurationProperties(InstagramProperties.class)
public class InstagramClient {

	public enum ContainerStatus {
		IN_PROGRESS, FINISHED, PUBLISHED, ERROR, EXPIRED
	}

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final RestClient restClient;
	private final String baseUrl;
	private final String apiVersion;
	private final String userId;

	public InstagramClient(RestClient.Builder builder, InstagramProperties properties) {
		this.restClient = builder.build();
		this.baseUrl = properties.baseUrl();
		this.apiVersion = properties.apiVersion();
		this.userId = properties.userId() == null ? "" : properties.userId();
	}

	public String createImageContainer(String token, String imageUrl, boolean carouselItem, String captionOrNull) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("image_url", imageUrl);
		if (carouselItem) {
			form.add("is_carousel_item", "true");
		}
		if (captionOrNull != null) {
			form.add("caption", captionOrNull);
		}
		return postForId(token, userId + "/media", form);
	}

	public String createCarouselContainer(String token, List<String> childIds, String caption) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("media_type", "CAROUSEL");
		form.add("children", String.join(",", childIds));
		form.add("caption", caption);
		return postForId(token, userId + "/media", form);
	}

	public ContainerStatus containerStatus(String token, String containerId) {
		StatusResponse response = get(token, apiPath(containerId, "fields", "status_code"), StatusResponse.class);
		if (response == null || response.statusCode() == null) {
			throw new InstagramApiException(-1, null, "인스타그램 응답에 status_code가 없습니다.");
		}
		try {
			return ContainerStatus.valueOf(response.statusCode());
		} catch (IllegalArgumentException e) {
			return ContainerStatus.ERROR;
		}
	}

	/** 게시된 미디어 id. */
	public String publish(String token, String containerId) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("creation_id", containerId);
		return postForId(token, userId + "/media_publish", form);
	}

	/** 게시는 이미 성공했으므로 링크 조회 실패는 빈 값으로 둔다. */
	public Optional<String> permalink(String token, String mediaId) {
		try {
			PermalinkResponse response = get(token, apiPath(mediaId, "fields", "permalink"), PermalinkResponse.class);
			return response == null ? Optional.empty() : Optional.ofNullable(response.permalink());
		} catch (InstagramApiException e) {
			return Optional.empty();
		}
	}

	private UriComponentsBuilder apiPath(String id, String queryName, String queryValue) {
		return UriComponentsBuilder.fromUriString(baseUrl)
			.pathSegment(apiVersion, id)
			.queryParam(queryName, queryValue);
	}

	private String postForId(String token, String path, MultiValueMap<String, String> form) {
		form.add("access_token", token);
		String[] segments = path.split("/");
		URI uri = UriComponentsBuilder.fromUriString(baseUrl).pathSegment(apiVersion).pathSegment(segments)
			.build().encode().toUri();
		IdResponse response = call(token, () -> restClient.post()
			.uri(uri)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(IdResponse.class));
		if (response == null || response.id() == null) {
			throw new InstagramApiException(-1, null, "인스타그램 응답에 id가 없습니다.");
		}
		return response.id();
	}

	private <T> T get(String token, UriComponentsBuilder uri, Class<T> type) {
		URI target = uri.queryParam("access_token", token).build().encode().toUri();
		return call(token, () -> restClient.get().uri(target).retrieve().body(type));
	}

	private <T> T call(String token, Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientResponseException e) {
			throw toException(token, e);
		} catch (RuntimeException e) {
			// 연결 실패의 원본 메시지에는 토큰이 든 URL이 있을 수 있어 종류만 남긴다.
			throw new InstagramApiException(-1, null, "인스타그램 요청 실패: " + e.getClass().getSimpleName());
		}
	}

	private static InstagramApiException toException(String token, RestClientResponseException e) {
		try {
			JsonNode error = MAPPER.readTree(e.getResponseBodyAsString()).path("error");
			if (error.isObject() && error.has("code")) {
				Integer subcode = error.has("error_subcode") ? error.get("error_subcode").asInt() : null;
				String message = error.path("message").asString("");
				if (token != null && !token.isEmpty()) {
					message = message.replace(token, "***");
				}
				return new InstagramApiException(error.get("code").asInt(), subcode, message);
			}
		} catch (RuntimeException ignored) {
			// 본문이 JSON이 아니면 상태 코드만 알린다.
		}
		return new InstagramApiException(-1, null, "인스타그램 응답 오류: HTTP " + e.getStatusCode().value());
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record IdResponse(String id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record StatusResponse(@JsonProperty("status_code") String statusCode) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PermalinkResponse(String permalink) {
	}

}
