package com.kdrinker.brewery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdrinker.brewery.dto.BreweryResponse;
import com.kdrinker.brewery.service.BreweryService;
import com.kdrinker.common.GlobalExceptionHandler;
import com.kdrinker.common.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BreweryController.class)
@Import(GlobalExceptionHandler.class)
class BreweryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BreweryService breweryService;

	private static BreweryResponse sample() {
		return new BreweryResponse(1L, "화요", "서울", null, null, "hwayo", null, "MANUAL", true);
	}

	@Test
	void 이름이_비면_400과_fields다() throws Exception {
		// when
		var result = mockMvc.perform(post("/api/v1/breweries").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("name"));
	}

	@Test
	void 생성은_201이다() throws Exception {
		// given
		when(breweryService.create(any())).thenReturn(sample());

		// when
		var result = mockMvc.perform(post("/api/v1/breweries").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"화요\"}"));

		// then
		result.andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(1));
	}

	@Test
	void 검색어로_이름을_찾는다() throws Exception {
		// given
		when(breweryService.search(eq("화요"), eq(0), eq(20)))
				.thenReturn(new PageResponse<>(List.of(sample()), 0, 20, 1, 1));

		// when
		var result = mockMvc.perform(get("/api/v1/breweries").param("q", "화요"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].name").value("화요"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void size가_101이면_400이다() throws Exception {
		mockMvc.perform(get("/api/v1/breweries").param("size", "101")).andExpect(status().isBadRequest());
	}

}
