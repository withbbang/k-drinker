package com.kdrinker.instagram.controller;

import com.kdrinker.common.ApiResponse;
import com.kdrinker.instagram.dto.InstagramTokenResponse;
import com.kdrinker.instagram.service.InstagramTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instagram/token")
@RequiredArgsConstructor
public class InstagramTokenController {

	private final InstagramTokenService tokenService;

	@GetMapping
	public ApiResponse<InstagramTokenResponse> get() {
		return ApiResponse.success(InstagramTokenResponse.from(tokenService.status()));
	}

	@PostMapping("/reload")
	public ApiResponse<InstagramTokenResponse> reload() {
		return ApiResponse.success(InstagramTokenResponse.from(tokenService.reload()));
	}

}
