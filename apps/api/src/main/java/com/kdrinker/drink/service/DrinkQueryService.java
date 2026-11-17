package com.kdrinker.drink.service;

import com.kdrinker.drink.dto.DrinkResponse;
import com.kdrinker.drink.repository.DrinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrinkQueryService {

	private final DrinkRepository drinkRepository;

	/** 이미 정규화한 검색어를 받는다. */
	public List<DrinkResponse> search(String normalizedQuery) {
		return drinkRepository.findTop20ByNormalizedNameContainingOrderByNameAsc(normalizedQuery).stream()
				.map(DrinkResponse::from).toList();
	}

}
