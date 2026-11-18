package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.publish.PublishFailure;
import com.kdrinker.telegram.dto.InlineButton;
import com.kdrinker.telegram.dto.TelegramText;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MessageFormatterTest {

	private static final Clock CLOCK = Clock.systemUTC();

	private static Post post(String drinkName, String rating, String caption, String hashtags) {
		Post post = Post.receive(PostSource.TELEGRAM, null, rating == null ? null : new BigDecimal(rating), CLOCK);
		ReflectionTestUtils.setField(post, "id", 128L);
		ReflectionTestUtils.setField(post, "version", 2);
		if (drinkName != null) {
			post.assignDrink(Drink.of(drinkName, drinkName, null));
		}
		ReflectionTestUtils.setField(post, "caption", caption);
		ReflectionTestUtils.setField(post, "hashtags", hashtags);
		return post;
	}

	private static String mapUrl(String address) {
		return "https://map.naver.com/p/search/" + URLEncoder.encode(address, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static List<String> callbacks(TelegramText t) {
		return t.keyboard().stream().flatMap(List::stream).map(InlineButton::callbackData).toList();
	}

	private static List<String> labels(TelegramText t) {
		return t.keyboard().stream().flatMap(List::stream).map(InlineButton::text).toList();
	}

	@Test
	void overviewPublicData() {
		// given
		String caption = "본문\n<!--brewery-->\n🍶 화요\n<!--brewery-->\n#a #b";
		Post post = post("화요 25", "4.5", caption, "#a #b");
		Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);
		brewery.updateContact("경기 여주시 가남읍 1", "031-000-0000", "https://hwayo.example.com", "hwayo_official", null,
				"https://data.example.org/1");

		// when
		TelegramText text = MessageFormatter.overview(post, brewery, 3);

		// then
		assertThat(text.html()).isEqualTo("""
				게시 대기 · 화요 25 (★4.5)
				🍶 화요 (출처: 공공데이터)
				📍 <a href="%s">경기 여주시 가남읍 1</a>
				🏠 <a href="https://hwayo.example.com">홈페이지</a>
				📷 <a href="https://www.instagram.com/hwayo_official/">@hwayo_official</a>
				☎ 031-000-0000
				사진 3장 · 캡션 %d자 · 해시태그 2개""".formatted(mapUrl("경기 여주시 가남읍 1"),
				"본문\n🍶 화요\n#a #b".codePointCount(0, "본문\n🍶 화요\n#a #b".length())));
		assertThat(text.html()).contains("%20");
		assertThat(callbacks(text)).containsExactly("ok:128:2", "detail:128:2", "edit:128:2", "cancel:128:2");
		assertThat(labels(text)).containsExactly("확인", "상세", "수정", "취소");
		assertThat(text.keyboard()).hasSize(1);
	}

	@Test
	void overviewWebSearchAddsPerFieldSource() {
		// given
		Post post = post("문배술", "4.0", "c", "#a");
		Brewery brewery = Brewery.of("문배주양조원", "문배주양조원", BrewerySource.WEB_SEARCH);
		brewery.updateContact("경기 김포시 1", null, "https://m.example.com", "example_brewery",
				"https://blog.naver.com/x", "https://src.example.com/p");
		brewery.recordWebFill(List.of("phone"), "https://other");

		// when
		String html = MessageFormatter.overview(post, brewery, 2).html();

		// then
		String src = " <a href=\"https://src.example.com/p\">출처</a>";
		assertThat(html).contains("🍶 문배주양조원 (출처: 웹 검색)\n");
		assertThat(html).contains("경기 김포시 1</a>" + src + "\n");
		assertThat(html).contains("홈페이지</a>" + src + "\n");
		assertThat(html).contains("@example_brewery</a>" + src + "\n");
		assertThat(html).contains("🔗 <a href=\"https://blog.naver.com/x\">블로그</a>" + src + "\n");
		assertThat(html).doesNotContain("☎");
	}

	@Test
	void overviewWebFilledPhoneOnPublicData() {
		// given
		Post post = post("문배술", "4.0", "c", "#a");
		Brewery brewery = Brewery.of("문배주양조원", "문배주양조원", BrewerySource.PUBLIC_DATA);
		brewery.updateContact("경기 김포시 1", "031-1", null, null, null, null);
		brewery.recordWebFill(List.of("phone"), "https://p");

		// when
		String html = MessageFormatter.overview(post, brewery, 2).html();

		// then
		assertThat(html).contains("☎ 031-1 <a href=\"https://p\">출처</a>\n");
		assertThat(html).doesNotContain("김포시 1</a> <a");
	}

	@Test
	void overviewOtherSnsNamesByHost() {
		// given
		Post post = post("술", "4.0", "c", "#a");
		Brewery brewery = Brewery.of("양조장", "양조장", BrewerySource.PUBLIC_DATA);
		brewery.updateContact(null, null, null, null,
				"https://www.youtube.com/@a, https://youtu.be/b https://facebook.com/c https://x.example.com/d 무시",
				null);

		// when
		String html = MessageFormatter.overview(post, brewery, 1).html();

		// then
		assertThat(html).contains("🔗 <a href=\"https://www.youtube.com/@a\">유튜브</a>\n");
		assertThat(html).contains("🔗 <a href=\"https://youtu.be/b\">유튜브</a>\n");
		assertThat(html).contains("🔗 <a href=\"https://facebook.com/c\">페이스북</a>\n");
		assertThat(html).contains("🔗 <a href=\"https://x.example.com/d\">기타 SNS</a>\n");
		assertThat(html).doesNotContain("무시");
	}

	@Test
	void overviewWithoutBrewery() {
		// given
		Post post = post("이름 모를 막걸리", null, "캡션", "#a #b #c");

		// when
		TelegramText text = MessageFormatter.overview(post, null, 1);

		// then
		assertThat(text.html()).isEqualTo("""
				⚠ 양조장 미확인
				게시 대기 · 이름 모를 막걸리 (별점 없음)
				양조장 정보 없이 게시됩니다. 캡션의 양조장 블록은 비어 있습니다.
				사진 1장 · 캡션 2자 · 해시태그 3개""");
		assertThat(callbacks(text)).hasSize(4);
	}

	@Test
	void ratingFormat() {
		assertThat(MessageFormatter.overview(post("a", "4", "c", ""), null, 1).html()).contains("(★4.0)");
		assertThat(MessageFormatter.overview(post("a", "4.5", "c", ""), null, 1).html()).contains("(★4.5)");
	}

	@Test
	void escapesUserValues() {
		// given
		Post post = post("탁주 <생>&A", "4.0", "x <b>&\n<!--brewery-->\n<!--brewery-->\ny", "#a");
		Brewery brewery = Brewery.of("양조 <장>", "양조", BrewerySource.MANUAL);
		brewery.updateContact("주소 & <1>", null, null, null, null, null);

		// when
		String overview = MessageFormatter.overview(post, brewery, 1).html();
		String detail = MessageFormatter.detailCaption(post).html();

		// then
		assertThat(overview).contains("탁주 &lt;생&gt;&amp;A").contains("양조 &lt;장&gt; (출처: 직접 입력)")
				.contains("주소 &amp; &lt;1&gt;</a>");
		assertThat(detail).contains("x &lt;b&gt;&amp;").doesNotContain("brewery");
		assertThat(detail).startsWith("캡션 원문 (");
		assertThat(MessageFormatter.canceled(post).html()).contains("탁주 &lt;생&gt;&amp;A");
	}

	@Test
	void analysisFailed() {
		// given
		Post post = post(null, null, null, null);
		post.recordFailure(null, "사진에서 술 이름을 읽지 못했습니다.");

		// when
		TelegramText text = MessageFormatter.analysisFailed(post);

		// then
		assertThat(text.html()).isEqualTo("분석에 실패했습니다.\n사유: 사진에서 술 이름을 읽지 못했습니다.");
		assertThat(callbacks(text)).containsExactly("retry:128:2", "edit:128:2");
		assertThat(labels(text)).containsExactly("재시도", "수정");
	}

	@Test
	void publishFailedCaptionTooLongHasEditOnly() {
		// given
		Post post = post("화요 25", "4.5", "c", "#a");
		post.recordFailure(null, PublishFailure.CAPTION_TOO_LONG.message());

		// when
		TelegramText text = MessageFormatter.publishFailed(post);

		// then
		assertThat(text.html()).isEqualTo("게시에 실패했습니다. 화요 25\n사유: 캡션이 2,200자를 넘었습니다.");
		assertThat(callbacks(text)).containsExactly("edit:128:2");
	}

	@Test
	void publishFailedOtherHasRetryOnly() {
		// given
		Post post = post("화요 25", "4.5", "c", "#a");
		post.recordFailure(null, PublishFailure.IMAGE_FETCH.message());

		// when
		TelegramText text = MessageFormatter.publishFailed(post);

		// then
		assertThat(text.html()).isEqualTo("게시에 실패했습니다. 화요 25\n사유: 인스타그램이 이미지를 가져가지 못했습니다.");
		assertThat(callbacks(text)).containsExactly("retry:128:2");
	}

	@Test
	void withoutDrinkNameHasNoTrailingSpace() {
		// given
		Post post = post(null, null, null, null);
		post.recordFailure(null, PublishFailure.UNKNOWN.message());

		// then
		assertThat(MessageFormatter.publishFailed(post).html()).startsWith("게시에 실패했습니다.\n사유:");
		assertThat(MessageFormatter.canceled(post).html()).isEqualTo("게시를 취소했습니다.\n사진은 7일 뒤에 삭제됩니다.");
		assertThat(MessageFormatter.publishing(post).html()).isEqualTo("인스타그램에 게시하는 중입니다.");
	}

	@Test
	void publishedWithPermalink() {
		// given
		Post post = post("화요 25", "4.5", "c", "#a");
		post.recordPublished("1", "https://www.instagram.com/p/abc/", Instant.now());

		// then
		assertThat(MessageFormatter.published(post).html())
				.isEqualTo("게시했습니다. 화요 25\n<a href=\"https://www.instagram.com/p/abc/\">게시물 보기</a>");
	}

	@Test
	void publishedWithoutPermalink() {
		// given
		Post post = post("화요 25", "4.5", "c", "#a");

		// then
		assertThat(MessageFormatter.published(post).html())
				.isEqualTo("게시했습니다. 화요 25\n게시물 주소를 가져오지 못했습니다. 인스타그램 앱에서 확인해 주세요.");
	}

	@Test
	void simpleMessages() {
		Post post = post("화요 25", "4.5", "c", "#a");
		assertThat(MessageFormatter.received(3).html())
				.isEqualTo("접수했습니다. 사진 3장\n분석이 끝나면 개요를 보내 드립니다.");
		assertThat(MessageFormatter.editLink("https://w/e?a=1&b=2").html()).isEqualTo(
				"수정 화면에서 고칠 수 있습니다.\n<a href=\"https://w/e?a=1&amp;b=2\">수정하기</a>\n저장하면 이후 확인과 게시는 웹 화면에서 진행합니다.");
		assertThat(MessageFormatter.canceled(post).html()).isEqualTo("게시를 취소했습니다. 화요 25\n사진은 7일 뒤에 삭제됩니다.");
		assertThat(MessageFormatter.publishing(post).html()).isEqualTo("인스타그램에 게시하는 중입니다. 화요 25");
		assertThat(MessageFormatter.textOnly().html())
				.isEqualTo("사진이 없어서 게시물을 만들 수 없습니다.\n사진을 보내면서 메모를 함께 적어 주세요.");
		assertThat(MessageFormatter.tooManyPhotos(12).html()).isEqualTo(
				"사진이 12장이라 접수하지 못했습니다.\n한 게시물에는 사진을 최대 10장까지 올릴 수 있습니다. 10장 이하로 나누어 다시 보내 주세요.");
		assertThat(MessageFormatter.textOnly().keyboard()).isEmpty();
	}

	@Test
	void intakeFailureNotices() {
		assertThat(MessageFormatter.photoTooLarge().html())
				.isEqualTo("사진이 너무 커서 받지 못했습니다. 20MB 이하로 다시 보내 주세요.");
		assertThat(MessageFormatter.unsupportedAttachment().html())
				.isEqualTo("사진만 받을 수 있습니다. 사진으로 다시 보내 주세요.");
		assertThat(MessageFormatter.intakeFailed().html()).isEqualTo("사진을 받지 못했습니다. 잠시 뒤 다시 보내 주세요.");
		assertThat(MessageFormatter.intakeFailed().keyboard()).isEmpty();
	}

	@Test
	void staleButton() {
		assertThat(MessageFormatter.staleButton()).isEqualTo("이미 처리했거나 오래된 버튼입니다. 최신 개요 메시지를 확인해 주세요.");
	}

	@Test
	void accepted() {
		assertThat(MessageFormatter.accepted("ok")).isEqualTo("게시를 시작합니다.");
		assertThat(MessageFormatter.accepted("cancel")).isEqualTo("취소했습니다.");
		assertThat(MessageFormatter.accepted("retry")).isEqualTo("다시 시도합니다.");
		assertThat(MessageFormatter.accepted("detail")).isNull();
		assertThat(MessageFormatter.accepted("edit")).isNull();
	}

}
