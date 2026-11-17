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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

	/** 웹 검색으로 채운 칸 이름(쉼표 구분). 공공데이터 양조장의 빈 칸을 보강할 때 남긴다. */
	@Column(name = "web_fields")
	private String webFields;

	/** {@link #webFields} 칸을 채운 웹 검색 출처 URL. */
	@Column(name = "web_source_url", length = 1000)
	private String webSourceUrl;

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

	/**
	 * 사용자가 직접 고친 값으로 덮어쓴다. 빈 문자열은 null로 둔다. 사용자가 본 값이므로 웹 출처 표시는 지운다.
	 */
	public void editManually(String name, String normalizedName, String address, String phone, String homepage,
			String instagramHandle, String otherSns) {
		this.name = name;
		this.normalizedName = normalizedName;
		this.address = blankToNull(address);
		this.phone = blankToNull(phone);
		this.homepage = blankToNull(homepage);
		this.instagramHandle = blankToNull(instagramHandle);
		this.otherSns = blankToNull(otherSns);
		this.source = BrewerySource.MANUAL;
		this.verified = true;
		this.sourceUrl = null;
		this.webFields = null;
		this.webSourceUrl = null;
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

	/** 웹 검색으로 채운 칸을 기존 기록과 합쳐 남긴다. 빈 목록이면 아무것도 하지 않는다. */
	public void recordWebFill(List<String> fields, String sourceUrl) {
		if (fields == null || fields.isEmpty()) {
			return;
		}
		Set<String> merged = new LinkedHashSet<>();
		if (!isBlank(webFields)) {
			merged.addAll(Arrays.asList(webFields.split(",")));
		}
		merged.addAll(fields);
		this.webFields = String.join(",", merged);
		this.webSourceUrl = sourceUrl;
	}

	/**
	 * 그 칸의 웹 검색 출처 URL. 양조장 자체가 웹 검색 출처이고 값이 있으면 sourceUrl, 아니면 웹으로 채운 기록이 있을 때
	 * webSourceUrl. 칸 이름: address phone homepage instagram otherSns.
	 */
	public Optional<String> webSourceOf(String field) {
		Optional<String> url;
		if (source == BrewerySource.WEB_SEARCH && !isBlank(valueOf(field))) {
			url = Optional.ofNullable(sourceUrl);
		} else if (!isBlank(webFields) && Arrays.asList(webFields.split(",")).contains(field)) {
			url = Optional.ofNullable(webSourceUrl);
		} else {
			url = Optional.empty();
		}
		return url.filter(u -> !u.isBlank());
	}

	private String valueOf(String field) {
		return switch (field) {
			case "address" -> address;
			case "phone" -> phone;
			case "homepage" -> homepage;
			case "instagram" -> instagramHandle;
			case "otherSns" -> otherSns;
			default -> null;
		};
	}

	private static String blankToNull(String s) {
		return isBlank(s) ? null : s;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

}
