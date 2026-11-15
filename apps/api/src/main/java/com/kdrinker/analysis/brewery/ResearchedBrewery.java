package com.kdrinker.analysis.brewery;

/** 웹 검색으로 찾은 양조장 정보. 모르는 값은 null. sourceUrl은 실제 검색 결과에 나온 URL이다. */
public record ResearchedBrewery(String name, String address, String phone, String homepage,
		String instagramHandle, String sourceUrl) {
}
