package com.kdrinker.brewery.controller;

import com.kdrinker.brewery.dto.BreweryRequest;
import com.kdrinker.brewery.dto.BreweryResponse;
import com.kdrinker.brewery.service.BreweryService;
import com.kdrinker.common.ApiResponse;
import com.kdrinker.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/breweries")
@RequiredArgsConstructor
public class BreweryController {

	private final BreweryService breweryService;

	@GetMapping
	public ApiResponse<PageResponse<BreweryResponse>> search(
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(breweryService.search(q, page, size));
	}

	@GetMapping("/{id}")
	public ApiResponse<BreweryResponse> detail(@PathVariable("id") Long id) {
		return ApiResponse.success(breweryService.get(id));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<BreweryResponse>> create(@Valid @RequestBody BreweryRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(breweryService.create(request)));
	}

	@PutMapping("/{id}")
	public ApiResponse<BreweryResponse> update(@PathVariable("id") Long id,
			@Valid @RequestBody BreweryRequest request) {
		return ApiResponse.success(breweryService.update(id, request));
	}

}
