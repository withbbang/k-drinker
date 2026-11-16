package com.kdrinker.instagram.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdrinker.auth.AuthFilterConfig;
import com.kdrinker.auth.SessionAuthFilter;
import com.kdrinker.auth.service.AuthService;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.common.GlobalExceptionHandler;
import com.kdrinker.instagram.service.InstagramTokenService;
import com.kdrinker.instagram.service.TokenStatus;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = InstagramTokenController.class, properties = "app.auth.enabled=true")
@Import({ GlobalExceptionHandler.class, AuthFilterConfig.class })
class InstagramTokenControllerTest {

	private static final TokenStatus STATUS = new TokenStatus(Instant.parse("2027-01-15T00:00:00Z"),
			Instant.parse("2026-11-16T00:00:00Z"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InstagramTokenService tokenService;

	@MockitoBean
	private AuthService authService;

	private Cookie session() {
		when(authService.isValid("good")).thenReturn(true);
		return new Cookie(SessionAuthFilter.COOKIE_NAME, "good");
	}

	@Test
	void 조회_응답에_accessToken이_없다() throws Exception {
		// given
		when(tokenService.status()).thenReturn(STATUS);

		// when
		var result = mockMvc.perform(get("/api/v1/instagram/token").cookie(session()));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.expiresAt").value("2027-01-15T00:00:00Z"))
			.andExpect(jsonPath("$.data.refreshedAt").value("2026-11-16T00:00:00Z"))
			.andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("accessToken"))));
	}

	@Test
	void reload_응답에도_accessToken이_없다() throws Exception {
		// given
		when(tokenService.reload()).thenReturn(STATUS);

		// when
		var result = mockMvc.perform(post("/api/v1/instagram/token/reload").cookie(session()));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.expiresAt").exists())
			.andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("accessToken"))));
	}

	@Test
	void 환경변수가_비면_400_INSTAGRAM_TOKEN_MISSING이다() throws Exception {
		// given
		when(tokenService.reload()).thenThrow(new BusinessException(ErrorCode.INSTAGRAM_TOKEN_MISSING));

		// when
		var result = mockMvc.perform(post("/api/v1/instagram/token/reload").cookie(session()));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INSTAGRAM_TOKEN_MISSING"));
	}

	@Test
	void 미로그인은_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/instagram/token")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/instagram/token/reload")).andExpect(status().isUnauthorized());
	}

}
