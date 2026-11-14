package com.kdrinker.auth;

import com.kdrinker.auth.service.AuthService;
import com.kdrinker.common.ApiResponse;
import com.kdrinker.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** 건강 확인과 로그인 외의 모든 요청에 세션을 요구한다. 미디어 prefix 외의 공개 경로는 정확히 일치할 때만 공개한다. */
@Slf4j
public class SessionAuthFilter extends OncePerRequestFilter {

	public static final String COOKIE_NAME = "KD_SESSION";
	public static final List<String> PUBLIC_PATHS = List.of("/api/v1/health", "/api/v1/sessions");
	public static final List<String> PUBLIC_PREFIXES = List.of("/api/v1/media/");

	private final AuthService authService;
	private final ObjectMapper objectMapper;

	public SessionAuthFilter(AuthService authService, ObjectMapper objectMapper) {
		this.authService = authService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (isPublic(request)) {
			chain.doFilter(request, response);
			return;
		}

		boolean valid;
		try {
			valid = authService.isValid(sessionToken(request));
		} catch (RuntimeException e) {
			log.error("세션 확인 실패", e);
			writeFailure(response, ErrorCode.COMMON_INTERNAL_ERROR);
			return;
		}
		if (!valid) {
			writeFailure(response, ErrorCode.AUTH_REQUIRED);
			return;
		}
		chain.doFilter(request, response);
	}

	private static boolean isPublic(HttpServletRequest request) {
		String method = request.getMethod();
		String uri = request.getRequestURI();
		if (uri == null || isSuspicious(uri)) {
			return false;
		}
		String contextPath = request.getContextPath();
		String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
				? uri.substring(contextPath.length())
				: uri;
		if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
			return "GET".equals(method);
		}
		return PUBLIC_PATHS.contains(path)
				&& ("GET".equals(method) && path.equals(PUBLIC_PATHS.get(0))
						|| "POST".equals(method) && path.equals(PUBLIC_PATHS.get(1)));
	}

	/** 경로 우회(.. · 인코딩된 점/슬래시 · 역슬래시 · 세미콜론)가 가능한 요청은 공개로 보지 않는다. */
	private static boolean isSuspicious(String uri) {
		String lower = uri.toLowerCase(Locale.ROOT);
		return lower.contains("..") || lower.contains("%2e") || lower.contains("%2f") || lower.contains("\\")
				|| lower.contains("%5c") || lower.contains(";");
	}

	private static String sessionToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (COOKIE_NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private void writeFailure(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getOutputStream().write(objectMapper.writeValueAsBytes(ApiResponse.failure(code)));
	}

}
