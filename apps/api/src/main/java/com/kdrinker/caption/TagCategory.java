package com.kdrinker.caption;

/** 해시태그 구성표의 구분. 고정 태그는 코드가 정하므로 Claude가 고르지 않는다. */
public enum TagCategory {
	/** 술 종류(약주 청주 증류주 과실주 등). 막걸리는 고정 태그에 있다. */
	TYPE,
	/** 술 이름. 공백 없이 붙인다. */
	NAME,
	/** 양조장 주소의 시군구(없으면 시도). */
	REGION,
	/** 양조장 이름. 법인 표기 제외. */
	BREWERY,
	/** 메모에 나온 페어링·맛. */
	PAIRING_TASTE
}
