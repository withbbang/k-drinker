package com.kdrinker.auth.controller;

import com.kdrinker.auth.SessionAuthFilter;
import com.kdrinker.auth.dto.SessionCreateRequest;
import com.kdrinker.auth.service.AuthService;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@ConditionalOnProperty("app.auth.enabled")
public class SessionController {

	private final AuthService authService;
	private final boolean cookieSecure;

	public SessionController(AuthService authService, @Value("${app.auth.cookie-secure}") boolean cookieSecure) {
		this.authService = authService;
		this.cookieSecure = cookieSecure;
	}

	@PostMapping
	public ResponseEntity<Void> create(@Valid @RequestBody SessionCreateRequest request) {
		String token = authService.login(request.code());
		return withCookie(token, AuthService.SESSION_LIFETIME);
	}

	/** 필터를 통과했다면 로그인된 상태다. 세션이 없으면 필터가 401을 돌려준다. */
	@GetMapping("/current")
	public ResponseEntity<Void> current() {
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(
			@CookieValue(name = SessionAuthFilter.COOKIE_NAME, required = false) String token) {
		authService.logout(token);
		return withCookie("", Duration.ZERO);
	}

	private ResponseEntity<Void> withCookie(String value, Duration maxAge) {
		ResponseCookie cookie = ResponseCookie.from(SessionAuthFilter.COOKIE_NAME, value)
				.httpOnly(true)
				.secure(cookieSecure)
				.sameSite("Strict")
				.path("/")
				.maxAge(maxAge)
				.build();
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
	}

}
