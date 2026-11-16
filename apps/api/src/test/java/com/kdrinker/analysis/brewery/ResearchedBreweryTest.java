package com.kdrinker.analysis.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ResearchedBreweryTest {

	private static ResearchedBrewery withHandle(String handle) {
		return new ResearchedBrewery("새술양조장", null, null, null, handle, "https://a.example.com");
	}

	private static ResearchedBrewery withHomepage(String homepage) {
		return new ResearchedBrewery("새술양조장", null, null, homepage, null, "https://a.example.com");
	}

	@Test
	void 인스타그램_계정은_앞의_골뱅이와_URL을_떼고_남긴다() {
		assertThat(withHandle("@sool_official").sanitized().instagramHandle()).isEqualTo("sool_official");
		assertThat(withHandle(" @ sool.official ").sanitized().instagramHandle()).isEqualTo("sool.official");
		assertThat(withHandle("https://www.instagram.com/sool.official/").sanitized().instagramHandle())
				.isEqualTo("sool.official");
		assertThat(withHandle("instagram.com/sool_official?hl=ko").sanitized().instagramHandle())
				.isEqualTo("sool_official");
		assertThat(withHandle("a".repeat(30)).sanitized().instagramHandle()).isEqualTo("a".repeat(30));
	}

	@Test
	void 형식에_맞지_않는_인스타그램_계정은_버린다() {
		assertThat(withHandle("새술 공식").sanitized().instagramHandle()).isNull();
		assertThat(withHandle("a".repeat(31)).sanitized().instagramHandle()).isNull();
		assertThat(withHandle("@").sanitized().instagramHandle()).isNull();
		assertThat(withHandle("sool-official").sanitized().instagramHandle()).isNull();
		assertThat(withHandle(null).sanitized().instagramHandle()).isNull();
	}

	@Test
	void 홈페이지는_http나_https_URL만_남긴다() {
		assertThat(withHomepage("https://sool.example.com").sanitized().homepage()).isEqualTo("https://sool.example.com");
		assertThat(withHomepage("HTTP://sool.example.com/a").sanitized().homepage()).isEqualTo("HTTP://sool.example.com/a");
		assertThat(withHomepage("sool.example.com").sanitized().homepage()).isNull();
		assertThat(withHomepage("javascript:alert(1)").sanitized().homepage()).isNull();
		assertThat(withHomepage("ftp://sool.example.com").sanitized().homepage()).isNull();
		assertThat(withHomepage("https://").sanitized().homepage()).isNull();
		assertThat(withHomepage(null).sanitized().homepage()).isNull();
	}

	@Test
	void 컬럼_길이를_넘는_값은_버린다() {
		// given
		String url1000 = "https://a.example.com/" + "a".repeat(1000 - 22);
		ResearchedBrewery brewery = new ResearchedBrewery("가".repeat(255), "주".repeat(256), "0".repeat(255),
				url1000 + "a", null, url1000);

		// when
		ResearchedBrewery sanitized = brewery.sanitized();

		// then
		assertThat(sanitized.name()).hasSize(255);
		assertThat(sanitized.address()).isNull();
		assertThat(sanitized.phone()).hasSize(255);
		assertThat(sanitized.homepage()).isNull();
		assertThat(sanitized.sourceUrl()).hasSize(1000);
	}

	@Test
	void 이름이_컬럼_길이를_넘으면_이름을_비운다() {
		assertThat(new ResearchedBrewery("가".repeat(256), null, null, null, null, "https://a.example.com")
				.sanitized().name()).isNull();
	}

	@Test
	void 길이를_넘어_버린_칸은_이름과_길이만_로그에_남긴다(CapturedOutput output) {
		// given
		String address = "주소값" + "가".repeat(253);
		ResearchedBrewery candidate = new ResearchedBrewery("새술양조장", address, "031-000-0000", null, null,
				"https://a.example.com");

		// when
		ResearchedBrewery sanitized = candidate.sanitized();

		// then
		assertThat(sanitized.address()).isNull();
		assertThat(output).contains("field=address").contains("length=256").contains("max=255");
		assertThat(output).doesNotContain("주소값").doesNotContain("field=phone");
	}
}
