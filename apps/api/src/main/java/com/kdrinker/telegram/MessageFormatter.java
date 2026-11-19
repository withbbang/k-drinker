package com.kdrinker.telegram;

import com.kdrinker.analysis.AnalysisFailure;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.caption.CaptionAssembler;
import com.kdrinker.post.entity.Post;
import com.kdrinker.publish.PublishFailure;
import com.kdrinker.telegram.dto.InlineButton;
import com.kdrinker.telegram.dto.TelegramText;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 텔레그램 메시지와 버튼 문구. 문구는 docs/telegram-messages.md를 그대로 따른다(바꾸면 문서도 함께 고친다).
 * parse_mode HTML이므로 고정 문구 밖의 값은 {@link #escape}로 이스케이프한다.
 */
public final class MessageFormatter {

	private MessageFormatter() {
	}

	public static TelegramText received(int photoCount) {
		return TelegramText.of("접수했습니다. 사진 " + photoCount + "장\n분석이 끝나면 개요를 보내 드립니다.");
	}

	public static TelegramText analysisFailed(Post post) {
		String reason = isBlank(post.getFailureReason()) ? AnalysisFailure.UNKNOWN.message() : post.getFailureReason();
		String html = "분석에 실패했습니다.\n사유: " + escape(reason);
		return new TelegramText(html, List.of(List.of(button("재시도", "retry", post), button("수정", "edit", post))));
	}

	public static TelegramText overview(Post post, Brewery brewery, int photoCount) {
		List<String> lines = new ArrayList<>();
		String head = "게시 대기 · " + escape(drinkName(post)) + " (" + rating(post.getRating()) + ")";
		String tail = "사진 " + photoCount + "장 · 캡션 " + captionLength(post) + "자 · 해시태그 "
				+ hashtagCount(post) + "개";
		if (brewery == null) {
			lines.add("⚠ 양조장 미확인");
			lines.add(head);
			lines.add("양조장 정보 없이 게시됩니다. 캡션의 양조장 블록은 비어 있습니다.");
		} else {
			lines.add(head);
			lines.addAll(BreweryLines.of(brewery));
		}
		lines.add(tail);
		List<InlineButton> row = List.of(button("확인", "ok", post), button("상세", "detail", post),
				button("수정", "edit", post), button("취소", "cancel", post));
		return new TelegramText(String.join("\n", lines), List.of(row));
	}

	public static TelegramText detailCaption(Post post) {
		String caption = post.getCaption() == null ? "" : CaptionAssembler.stripMarkers(post.getCaption());
		int length = caption.codePointCount(0, caption.length());
		return TelegramText.of("캡션 원문 (" + length + "자)\n\n" + escape(caption));
	}

	public static TelegramText editLink(String url) {
		return TelegramText.of("수정 화면에서 고칠 수 있습니다.\n<a href=\"" + escapeAttr(url)
				+ "\">수정하기</a>\n저장하면 이후 확인과 게시는 웹 화면에서 진행합니다.");
	}

	public static TelegramText handoff(String url) {
		return TelegramText.of("검토와 업로드는 k-drinker에서 이어서 진행해 주세요.\n<a href=\"" + escapeAttr(url)
				+ "\">웹에서 열기</a>");
	}

	public static TelegramText canceled(Post post) {
		return TelegramText.of(withName("게시를 취소했습니다.", post) + "\n사진은 7일 뒤에 삭제됩니다.");
	}

	public static TelegramText publishing(Post post) {
		return TelegramText.of(withName("인스타그램에 게시하는 중입니다.", post));
	}

	public static TelegramText published(Post post) {
		String second = isBlank(post.getPermalink())
				? "게시물 주소를 가져오지 못했습니다. 인스타그램 앱에서 확인해 주세요."
				: "<a href=\"" + escapeAttr(post.getPermalink()) + "\">게시물 보기</a>";
		return TelegramText.of(withName("게시했습니다.", post) + "\n" + second);
	}

	public static TelegramText publishFailed(Post post) {
		String reason = isBlank(post.getFailureReason()) ? PublishFailure.UNKNOWN.message() : post.getFailureReason();
		String html = withName("게시에 실패했습니다.", post) + "\n사유: " + escape(reason);
		InlineButton button = PublishFailure.CAPTION_TOO_LONG.message().equals(reason) ? button("수정", "edit", post)
				: button("재시도", "retry", post);
		return new TelegramText(html, List.of(List.of(button)));
	}

	public static TelegramText textOnly() {
		return TelegramText.of("사진이 없어서 게시물을 만들 수 없습니다.\n사진을 보내면서 메모를 함께 적어 주세요.");
	}

	public static TelegramText tooManyPhotos(int count) {
		return TelegramText.of("사진이 " + count + "장이라 접수하지 못했습니다.\n"
				+ "한 게시물에는 사진을 최대 10장까지 올릴 수 있습니다. 10장 이하로 나누어 다시 보내 주세요.");
	}

	public static TelegramText photoTooLarge() {
		return TelegramText.of("사진이 너무 커서 받지 못했습니다. 20MB 이하로 다시 보내 주세요.");
	}

	public static TelegramText unsupportedAttachment() {
		return TelegramText.of("사진만 받을 수 있습니다. 사진으로 다시 보내 주세요.");
	}

	public static TelegramText intakeFailed() {
		return TelegramText.of("사진을 받지 못했습니다. 잠시 뒤 다시 보내 주세요.");
	}

	/** 오래된 버튼 팝업. */
	public static String staleButton() {
		return "이미 처리했거나 오래된 버튼입니다. 최신 개요 메시지를 확인해 주세요.";
	}

	/** 처리 접수 팝업. ok cancel retry 외에는 팝업이 없어 null. */
	public static String accepted(String act) {
		if (act == null) {
			return null;
		}
		return switch (act) {
			case "ok" -> "게시를 시작합니다.";
			case "cancel" -> "취소했습니다.";
			case "retry" -> "다시 시도합니다.";
			default -> null;
		};
	}

	/** 본문 값의 HTML 특수문자 이스케이프. */
	static String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** href 속성 값 이스케이프. 따옴표까지 처리한다. */
	static String escapeAttr(String s) {
		return escape(s).replace("\"", "&quot;");
	}

	static String mapUrl(String address) {
		return "https://map.naver.com/p/search/" + URLEncoder.encode(address, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/** 술 이름이 있으면 "문구 이름", 없으면 이름과 앞 공백 없이 문구만. */
	private static String withName(String text, Post post) {
		String name = drinkName(post);
		return name.isBlank() ? text : text + " " + escape(name);
	}

	private static String drinkName(Post post) {
		return post.getDrink() == null || post.getDrink().getName() == null ? "" : post.getDrink().getName();
	}

	private static String rating(BigDecimal rating) {
		return rating == null ? "별점 없음" : "★" + rating.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString();
	}

	private static int captionLength(Post post) {
		return post.getCaption() == null ? 0 : CaptionAssembler.length(post.getCaption());
	}

	private static int hashtagCount(Post post) {
		return post.getHashtags() == null ? 0 : (int) post.getHashtags().chars().filter(c -> c == '#').count();
	}

	private static InlineButton button(String text, String act, Post post) {
		return new InlineButton(text, act + ":" + post.getId() + ":" + post.getVersion());
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	/** 개요의 양조장 줄들. 값이 있는 줄만, 웹 검색으로 채운 항목에는 줄 끝에 출처 링크를 붙인다. */
	private static final class BreweryLines {

		private BreweryLines() {
		}

		static List<String> of(Brewery b) {
			List<String> lines = new ArrayList<>();
			lines.add("🍶 " + escape(b.getName()) + sourceLabel(b.getSource()));
			if (!isBlank(b.getAddress())) {
				lines.add("📍 <a href=\"" + escapeAttr(mapUrl(b.getAddress())) + "\">" + escape(b.getAddress()) + "</a>"
						+ source(b, "address"));
			}
			if (!isBlank(b.getHomepage())) {
				lines.add("🏠 <a href=\"" + escapeAttr(b.getHomepage()) + "\">홈페이지</a>" + source(b, "homepage"));
			}
			if (!isBlank(b.getInstagramHandle())) {
				String handle = b.getInstagramHandle();
				lines.add("📷 <a href=\"" + escapeAttr("https://www.instagram.com/" + handle + "/") + "\">@"
						+ escape(handle) + "</a>" + source(b, "instagram"));
			}
			if (!isBlank(b.getOtherSns())) {
				for (String url : b.getOtherSns().split("[,\\s]+")) {
					if (url.startsWith("http://") || url.startsWith("https://")) {
						lines.add("🔗 <a href=\"" + escapeAttr(url) + "\">" + snsName(url) + "</a>" + source(b, "otherSns"));
					}
				}
			}
			if (!isBlank(b.getPhone())) {
				lines.add("☎ " + escape(b.getPhone()) + source(b, "phone"));
			}
			return lines;
		}

		private static String sourceLabel(BrewerySource source) {
			if (source == null) {
				return "";
			}
			return switch (source) {
				case PUBLIC_DATA -> " (출처: 공공데이터)";
				case WEB_SEARCH -> " (출처: 웹 검색)";
				case MANUAL -> " (출처: 직접 입력)";
			};
		}

		private static String source(Brewery b, String field) {
			return b.webSourceOf(field).map(url -> " <a href=\"" + escapeAttr(url) + "\">출처</a>").orElse("");
		}

		private static String snsName(String url) {
			String host;
			try {
				host = URI.create(url).getHost();
			} catch (IllegalArgumentException e) {
				host = null;
			}
			if (host == null) {
				return "기타 SNS";
			}
			host = host.toLowerCase(Locale.ROOT);
			if (is(host, "blog.naver.com")) {
				return "블로그";
			}
			if (is(host, "youtube.com") || is(host, "youtu.be")) {
				return "유튜브";
			}
			if (is(host, "facebook.com")) {
				return "페이스북";
			}
			return "기타 SNS";
		}

		private static boolean is(String host, String domain) {
			return host.equals(domain) || host.endsWith("." + domain);
		}
	}

}
