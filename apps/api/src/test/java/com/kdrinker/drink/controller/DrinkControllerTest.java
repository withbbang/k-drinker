package com.kdrinker.drink.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdrinker.common.GlobalExceptionHandler;
import com.kdrinker.drink.dto.DrinkResponse;
import com.kdrinker.drink.service.DrinkQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DrinkController.class)
@Import(GlobalExceptionHandler.class)
class DrinkControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DrinkQueryService drinkQueryService;

	@Test
	void 검색어가_비면_빈_배열이다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/drinks").param("q", "  "));

		// then
		result.andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
		verifyNoInteractions(drinkQueryService);
	}

	@Test
	void 정규화해서_찾는다() throws Exception {
		// given
		when(drinkQueryService.search("화요25")).thenReturn(List.of(new DrinkResponse(1L, "화요 25", 2L, "화요")));

		// when
		var result = mockMvc.perform(get("/api/v1/drinks").param("q", "화요 25"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].name").value("화요 25"))
				.andExpect(jsonPath("$.data[0].breweryName").value("화요"));
		verify(drinkQueryService).search("화요25");
	}

}
