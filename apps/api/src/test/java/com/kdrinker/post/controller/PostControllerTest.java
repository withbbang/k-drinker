package com.kdrinker.post.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdrinker.common.GlobalExceptionHandler;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.service.PostIntakeService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostIntakeService postIntakeService;

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

}
