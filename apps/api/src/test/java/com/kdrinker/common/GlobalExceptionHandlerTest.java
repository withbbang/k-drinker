package com.kdrinker.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({ GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class })
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 없는_경로는_COMMON_NOT_FOUND_404다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/nope"));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	@Test
	void 처리되지_않은_예외는_500이고_내부정보를_숨긴다() throws Exception {
		// when
		var result = mockMvc.perform(get("/test/unexpected"));

		// then
		result.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_ERROR"));
		String body = result.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("secret-detail").doesNotContain("IllegalStateException");
	}

	@Test
	void BusinessException은_ErrorCode의_상태와_메시지로_변환된다() throws Exception {
		// when
		var result = mockMvc.perform(get("/test/business"));

		// then
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"))
				.andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));
	}

	@Test
	void 검증_실패는_fields를_포함한다() throws Exception {
		// when
		var result = mockMvc.perform(
				post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{}"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("name"));
	}

	@Test
	void 필수_파라미터가_없으면_400이다() throws Exception {
		// when
		var result = mockMvc.perform(get("/test/param"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 지원하지_않는_Content_Type이면_500이_아니라_400이다() throws Exception {
		// when
		var result = mockMvc.perform(
				post("/test/validated").contentType(MediaType.TEXT_PLAIN).content("name"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	record NameRequest(@NotBlank String name) {
	}

	@RestController
	static class TestController {

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("secret-detail");
		}

		@GetMapping("/test/business")
		void business() {
			throw new BusinessException(ErrorCode.AUTH_REQUIRED);
		}

		@GetMapping("/test/param")
		void param(@RequestParam String q) {
		}

		@PostMapping("/test/validated")
		void validated(@Valid @RequestBody NameRequest request) {
		}

	}

}
