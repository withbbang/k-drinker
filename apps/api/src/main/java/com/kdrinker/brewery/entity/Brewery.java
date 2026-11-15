package com.kdrinker.brewery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "brewery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brewery {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Column(name = "normalized_name")
	private String normalizedName;

	private String address;

	private String phone;

	private String homepage;

	@Column(name = "instagram_handle")
	private String instagramHandle;

	@Column(name = "other_sns")
	private String otherSns;

	@Enumerated(EnumType.STRING)
	private BrewerySource source;

	@Column(name = "source_url")
	private String sourceUrl;

	private boolean verified;

	public static Brewery of(String name, String normalizedName, BrewerySource source) {
		Brewery brewery = new Brewery();
		brewery.name = name;
		brewery.normalizedName = normalizedName;
		brewery.source = source;
		return brewery;
	}

	public void markPublicData() {
		this.source = BrewerySource.PUBLIC_DATA;
	}

	public void markVerified() {
		this.verified = true;
	}

	public void updateContact(String address, String phone, String homepage, String instagramHandle,
			String otherSns, String sourceUrl) {
		this.address = address;
		this.phone = phone;
		this.homepage = homepage;
		this.instagramHandle = instagramHandle;
		this.otherSns = otherSns;
		this.sourceUrl = sourceUrl;
	}

}
