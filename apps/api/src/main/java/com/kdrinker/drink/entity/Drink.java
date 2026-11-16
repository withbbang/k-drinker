package com.kdrinker.drink.entity;

import com.kdrinker.brewery.entity.Brewery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "drink")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drink {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Column(name = "normalized_name")
	private String normalizedName;

	private String aliases;

	private String category;

	@Column(precision = 4, scale = 1)
	private BigDecimal abv;

	@Column(name = "volume_ml")
	private Integer volumeMl;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "brewery_id")
	private Brewery brewery;

	public static Drink of(String name, String normalizedName, Brewery brewery) {
		Drink drink = new Drink();
		drink.name = name;
		drink.normalizedName = normalizedName;
		drink.brewery = brewery;
		return drink;
	}

	/** 양조장이 비어 있을 때만 잇는다. 이미 있는 양조장은 바꾸지 않는다. */
	public void linkBreweryIfMissing(Brewery brewery) {
		if (this.brewery == null && brewery != null) {
			this.brewery = brewery;
		}
	}

	public void updateSpec(String category, BigDecimal abv, Integer volumeMl, String aliases) {
		this.category = category;
		this.abv = abv;
		this.volumeMl = volumeMl;
		this.aliases = aliases;
	}

}
