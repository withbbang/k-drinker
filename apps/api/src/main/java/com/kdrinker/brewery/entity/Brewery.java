package com.kdrinker.brewery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

	@Column(length = 1000)
	private String homepage;

	@Column(name = "instagram_handle")
	private String instagramHandle;

	@Column(name = "other_sns")
	private String otherSns;

	@Enumerated(EnumType.STRING)
	private BrewerySource source;

	@Column(name = "source_url", length = 1000)
	private String sourceUrl;

	private boolean verified;

	/** 웹 조사로 빈 칸을 채우려 한 시각. null이면 아직 조사하지 않았다. 양조장마다 한 번만 조사한다. */
	@Column(name = "researched_at")
	private Instant researchedAt;

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

	public void markResearched(Instant at) {
		this.researchedAt = at;
	}

	/** 주소·전화·홈페이지·인스타그램 중 하나라도 비어 있으면 true. */
	public boolean hasBlankContact() {
		return isBlank(address) || isBlank(phone) || isBlank(homepage) || isBlank(instagramHandle);
	}

	/** 비어 있는 칸만 채운다. 값이 있는 칸은 건드리지 않는다. 채운 칸 이름을 돌려준다. */
	public List<String> fillBlankContact(String address, String phone, String homepage, String instagramHandle) {
		List<String> filled = new ArrayList<>();
		if (isBlank(this.address) && !isBlank(address)) {
			this.address = address;
			filled.add("address");
		}
		if (isBlank(this.phone) && !isBlank(phone)) {
			this.phone = phone;
			filled.add("phone");
		}
		if (isBlank(this.homepage) && !isBlank(homepage)) {
			this.homepage = homepage;
			filled.add("homepage");
		}
		if (isBlank(this.instagramHandle) && !isBlank(instagramHandle)) {
			this.instagramHandle = instagramHandle;
			filled.add("instagram");
		}
		return filled;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

}
