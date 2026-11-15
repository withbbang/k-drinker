package com.kdrinker.brewery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "brewery_alias")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BreweryAlias {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "brewery_id")
	private Brewery brewery;

	@Column(name = "normalized_alias")
	private String normalizedAlias;

	public static BreweryAlias of(Brewery brewery, String normalizedAlias) {
		BreweryAlias alias = new BreweryAlias();
		alias.brewery = brewery;
		alias.normalizedAlias = normalizedAlias;
		return alias;
	}

}
