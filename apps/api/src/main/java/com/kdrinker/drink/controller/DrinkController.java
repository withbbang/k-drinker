package com.kdrinker.drink.controller;

import com.kdrinker.common.ApiResponse;
import com.kdrinker.common.Names;
import com.kdrinker.drink.dto.DrinkResponse;
import com.kdrinker.drink.service.DrinkQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drinks")
@RequiredArgsConstructor
public class DrinkController {

	private final DrinkQueryService drinkQueryService;

	@GetMapping
	public ApiResponse<List<DrinkResponse>> search(@RequestParam(name = "q", required = false) String q) {
		String normalized = Names.normalize(q);
		if (normalized.isEmpty()) {
			return ApiResponse.success(List.of());
		}
		return ApiResponse.success(drinkQueryService.search(normalized));
	}

}
