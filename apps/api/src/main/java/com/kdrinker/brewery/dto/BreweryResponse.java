package com.kdrinker.brewery.dto;

import com.kdrinker.brewery.entity.Brewery;

public record BreweryResponse(Long id, String name, String address, String phone, String homepage,
		String instagramHandle, String otherSns, String source, boolean verified) {

	public static BreweryResponse from(Brewery b) {
		return new BreweryResponse(b.getId(), b.getName(), b.getAddress(), b.getPhone(), b.getHomepage(),
				b.getInstagramHandle(), b.getOtherSns(), b.getSource() == null ? null : b.getSource().name(),
				b.isVerified());
	}
}
