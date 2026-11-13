package com.kdrinker.health.controller;

import com.kdrinker.common.ApiResponse;
import com.kdrinker.health.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/api/v1/health")
	public ApiResponse<HealthResponse> health() {
		return ApiResponse.success(new HealthResponse("UP"));
	}

}
