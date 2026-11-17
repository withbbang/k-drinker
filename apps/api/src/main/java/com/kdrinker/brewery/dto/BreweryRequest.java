package com.kdrinker.brewery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BreweryRequest(@NotBlank @Size(max = 255) String name, @Size(max = 255) String address,
		@Size(max = 255) String phone, @Size(max = 1000) String homepage, @Size(max = 255) String instagramHandle,
		@Size(max = 255) String otherSns) {
}
