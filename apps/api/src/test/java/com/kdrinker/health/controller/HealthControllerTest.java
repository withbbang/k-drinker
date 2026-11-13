package com.kdrinker.health.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdrinker.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(GlobalExceptionHandler.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 헬스체크는_UP을_돌려준다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/health"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("UP"))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

}
