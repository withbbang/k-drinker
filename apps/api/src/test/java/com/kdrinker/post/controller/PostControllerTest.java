package com.kdrinker.post.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdrinker.common.GlobalExceptionHandler;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.common.PageResponse;
import com.kdrinker.post.dto.PostSummaryResponse;
import com.kdrinker.post.dto.PostDetailResponse;
import com.kdrinker.post.service.PostActionService;
import com.kdrinker.post.service.PostEditService;
import com.kdrinker.post.service.PostIntakeService;
import com.kdrinker.post.service.PostQueryService;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostIntakeService postIntakeService;

	@MockitoBean
	private PostQueryService postQueryService;

	@MockitoBean
	private PostEditService postEditService;

	@MockitoBean
	private PostActionService postActionService;

	private ResultActions post(int photos, String rating) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/posts");
		for (int i = 0; i < photos; i++) {
			request.file(new MockMultipartFile("photos", "p" + i + ".jpg", "image/jpeg", new byte[] { 1 }));
		}
		request.param("source", "SHARE").param("memo", "맛있다");
		if (rating != null) {
			request.param("rating", rating);
		}
		return mockMvc.perform(request);
	}

	private void stubReceive() {
		Post saved = Post.receive(PostSource.SHARE, "맛있다", null, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
		ReflectionTestUtils.setField(saved, "id", 42L);
		when(postIntakeService.receiveInputs(eq(PostSource.SHARE), any(), any(), any())).thenReturn(saved);
	}

	@Test
	void 사진_3장이면_201과_RECEIVED다() throws Exception {
		// given
		stubReceive();

		// when
		var result = post(3, "4.5");

		// then
		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(42))
				.andExpect(jsonPath("$.data.status").value("RECEIVED"));
	}

	@Test
	void 사진_0장이나_11장이면_POST_PHOTO_COUNT다() throws Exception {
		// given
		stubReceive();

		// when / then
		post(0, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("POST_PHOTO_COUNT"));
		post(11, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("POST_PHOTO_COUNT"));
		post(1, null).andExpect(status().isCreated());
		post(10, null).andExpect(status().isCreated());
	}

	@Test
	void 별점_5_5나_4_3이면_POST_INVALID_RATING이다() throws Exception {
		// given
		stubReceive();

		// when / then
		post(1, "5.5").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("POST_INVALID_RATING"));
		post(1, "4.3").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("POST_INVALID_RATING"));
		post(1, "-0.5").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("POST_INVALID_RATING"));
		post(1, "0").andExpect(status().isCreated());
		post(1, "5").andExpect(status().isCreated());
		post(1, "4.5").andExpect(status().isCreated());
	}

	@Test
	void 메모가_2000자를_넘으면_검증_실패다() throws Exception {
		// when
		var result = mockMvc.perform(multipart("/api/v1/posts")
				.file(new MockMultipartFile("photos", "p.jpg", "image/jpeg", new byte[] { 1 }))
				.param("source", "SHARE").param("memo", "가".repeat(2001)));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 목록은_페이지_형식이다() throws Exception {
		// given
		var item = new PostSummaryResponse(7L, "PENDING_REVIEW", "막걸리", null, null, null, Instant.EPOCH, "WEB");
		when(postQueryService.list(0, 20)).thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));

		// when / then
		mockMvc.perform(get("/api/v1/posts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(7))
				.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void size_101은_400이다() throws Exception {
		// when / then
		mockMvc.perform(get("/api/v1/posts").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 없는_게시물은_404다() throws Exception {
		// given
		when(postQueryService.detail(99L)).thenThrow(new BusinessException(ErrorCode.COMMON_NOT_FOUND));

		// when / then
		mockMvc.perform(get("/api/v1/posts/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	private static MockMultipartFile requestPart(String json) {
		return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
	}

	private static MockMultipartHttpServletRequestBuilder putMultipart(String url) {
		return multipart(org.springframework.http.HttpMethod.PUT, url);
	}

	@Test
	void version이_없으면_400이다() throws Exception {
		// given
		String body = """
				{"captionHead": "훅", "captionTail": "", "photos": [{"id": 1, "fitMode": "CROP"}]}
				""";

		// when / then
		mockMvc.perform(putMultipart("/api/v1/posts/1").file(requestPart(body)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		verifyNoInteractions(postEditService);
	}

	@Test
	void 수정_저장은_multipart로_받는다() throws Exception {
		// given
		String body = """
				{"version": 3, "captionHead": "훅", "captionTail": "",
				 "photos": [{"newIndex": 0, "fitMode": "CROP"}, {"id": 1, "fitMode": "CROP"}]}
				""";
		when(postEditService.update(eq(1L), any(), org.mockito.ArgumentMatchers.argThat(list -> list.size() == 1)))
				.thenReturn(detailWithStatus("PENDING_REVIEW"));

		// when / then
		mockMvc.perform(putMultipart("/api/v1/posts/1").file(requestPart(body))
				.file(new MockMultipartFile("newPhotos", "a.jpg", "image/jpeg", new byte[] { 1 })))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
	}

	@Test
	void request_파트가_없으면_400() throws Exception {
		// when / then
		mockMvc.perform(putMultipart("/api/v1/posts/1")
				.file(new MockMultipartFile("newPhotos", "a.jpg", "image/jpeg", new byte[] { 1 })))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		verifyNoInteractions(postEditService);
	}

	@Test
	void request_파트가_깨진_JSON이면_400() throws Exception {
		// when / then
		mockMvc.perform(putMultipart("/api/v1/posts/1").file(requestPart("{not json")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		verifyNoInteractions(postEditService);
	}

	private static PostDetailResponse detailWithStatus(String status) {
		return new PostDetailResponse(5L, status, 3, false, null, null, null, null, null, null, null, "", "", "",
				false, 0, List.of(), null, "WEB", "", false);
	}

	@Test
	void 확인_취소_재시도는_200과_바뀐_상태를_돌려준다() throws Exception {
		// given
		when(postActionService.confirmFromWeb(5L, 2)).thenReturn(detailWithStatus("PUBLISHING"));
		when(postActionService.cancelFromWeb(5L, 2)).thenReturn(detailWithStatus("CANCELED"));
		when(postActionService.retryFromWeb(5L, 2)).thenReturn(detailWithStatus("ANALYZING"));
		String body = "{\"version\": 2}";

		// when / then
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/posts/5/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("PUBLISHING"));
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/posts/5/cancel").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELED"));
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/posts/5/retry").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ANALYZING"));
	}

	@Test
	void 동작_요청에_version이_없으면_400이다() throws Exception {
		// when / then
		for (String action : List.of("confirm", "cancel", "retry")) {
			mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/posts/5/" + action).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		}
		verifyNoInteractions(postActionService);
	}

	@Test
	void 동작_서비스의_409를_그대로_전달한다() throws Exception {
		// given
		when(postActionService.confirmFromWeb(5L, 2)).thenThrow(new BusinessException(ErrorCode.POST_NOT_EDITABLE));
		when(postActionService.cancelFromWeb(5L, 2))
			.thenThrow(new BusinessException(ErrorCode.POST_VERSION_CONFLICT));
		String body = "{\"version\": 2}";

		// when / then
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/posts/5/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_EDITABLE"));
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/posts/5/cancel").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("POST_VERSION_CONFLICT"));
	}
}
