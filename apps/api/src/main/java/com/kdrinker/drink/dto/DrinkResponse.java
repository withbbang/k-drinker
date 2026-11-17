package com.kdrinker.drink.dto;

import com.kdrinker.drink.entity.Drink;

public record DrinkResponse(Long id, String name, Long breweryId, String breweryName) {

	public static DrinkResponse from(Drink d) {
		return new DrinkResponse(d.getId(), d.getName(), d.getBrewery() == null ? null : d.getBrewery().getId(),
				d.getBrewery() == null ? null : d.getBrewery().getName());
	}
}
