package com.kdrinker.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.telegram.AlbumCollector.Accepted;
import com.kdrinker.telegram.AlbumCollector.Buffered;
import com.kdrinker.telegram.AlbumCollector.PendingAlbum;
import com.kdrinker.telegram.AlbumCollector.TextOnly;
import com.kdrinker.telegram.dto.Document;
import com.kdrinker.telegram.dto.PhotoSize;
import com.kdrinker.telegram.dto.TelegramMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlbumCollectorTest {

	private static final long CHAT = 100L;

	private MutableClock clock;
	private AlbumCollector collector;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-11-16T03:00:00Z"));
		collector = new AlbumCollector(clock);
	}

	@Test
	void 같은_media_group_id는_한_앨범으로_묶는다() {
		collector.accept(photo(1, "g1", "A1", "첫 장 캡션"));
		collector.accept(photo(2, "g1", "A2", null));
		collector.accept(photo(3, "g1", "A3", null));

		clock.advance(Duration.ofMillis(2900));
		assertThat(collector.drainDue()).isEmpty();

		clock.advance(Duration.ofMillis(100));
		List<PendingAlbum> due = collector.drainDue();
		assertThat(due).hasSize(1);
		assertThat(due.get(0).fileIds()).containsExactly("A1", "A2", "A3");
		assertThat(due.get(0).memo()).isEqualTo("첫 장 캡션");
		assertThat(due.get(0).chatId()).isEqualTo(CHAT);
		assertThat(collector.drainDue()).isEmpty();
	}

	@Test
	void 사진_순서는_message_id_오름차순이다() {
		collector.accept(photo(3, "g1", "A3", null));
		collector.accept(photo(1, "g1", "A1", null));
		collector.accept(photo(2, "g1", "A2", null));

		clock.advance(Duration.ofSeconds(3));
		assertThat(collector.drainDue().get(0).fileIds()).containsExactly("A1", "A2", "A3");
	}

	@Test
	void 여러_장의_캡션은_줄바꿈으로_합친다() {
		collector.accept(photo(1, "g1", "A1", "하나"));
		collector.accept(photo(2, "g1", "A2", "둘"));

		clock.advance(Duration.ofSeconds(3));
		assertThat(collector.drainDue().get(0).memo()).isEqualTo("하나\n둘");
	}

	@Test
	void 묶음_없는_사진_한_장도_3초_기다린다() {
		collector.accept(photo(1, null, "A1", null));

		clock.advance(Duration.ofMillis(2900));
		assertThat(collector.drainDue()).isEmpty();
		clock.advance(Duration.ofMillis(100));
		assertThat(collector.drainDue()).hasSize(1);
	}

	@Test
	void 창_안의_텍스트는_메모로_붙는다() {
		collector.accept(photo(1, null, "A1", "캡션"));
		clock.advance(Duration.ofSeconds(1));

		Accepted result = collector.accept(text(2, "메모입니다"));

		assertThat(result).isInstanceOf(Buffered.class);
		clock.advance(Duration.ofSeconds(3));
		assertThat(collector.drainDue().get(0).memo()).isEqualTo("캡션\n메모입니다");
	}

	@Test
	void 창_밖의_텍스트는_텍스트만이다() {
		collector.accept(photo(1, null, "A1", null));
		clock.advance(Duration.ofSeconds(5));

		Accepted result = collector.accept(text(2, "늦은 메모"));

		assertThat(result).isInstanceOf(TextOnly.class);
		assertThat(collector.drainDue().get(0).memo()).isNull();
	}

	@Test
	void 사진_없는_텍스트는_텍스트만이다() {
		assertThat(collector.accept(text(1, "안녕"))).isInstanceOf(TextOnly.class);
		assertThat(collector.drainAll()).isEmpty();
	}

	@Test
	void 다른_그룹의_사진이_오면_이전_앨범은_바로_마감한다() {
		collector.accept(photo(1, "gA", "A1", null));
		clock.advance(Duration.ofSeconds(1));
		collector.accept(photo(2, "gB", "B1", null));

		List<PendingAlbum> due = collector.drainDue();
		assertThat(due).hasSize(1);
		assertThat(due.get(0).fileIds()).containsExactly("A1");

		clock.advance(Duration.ofSeconds(3));
		assertThat(collector.drainDue().get(0).fileIds()).containsExactly("B1");
	}

	@Test
	void 이미지_문서는_받고_pdf는_무시한다() {
		TelegramMessage jpeg = new TelegramMessage(1, CHAT, null, null, "g", null,
				new Document("D1", "image/jpeg", 10L));
		TelegramMessage png = new TelegramMessage(2, CHAT, null, null, "g", null,
				new Document("D2", "image/png", 10L));
		TelegramMessage pdf = new TelegramMessage(3, CHAT, null, null, null, null,
				new Document("D3", "application/pdf", 10L));

		collector.accept(jpeg);
		collector.accept(png);
		collector.accept(pdf);

		clock.advance(Duration.ofSeconds(3));
		assertThat(collector.drainDue().get(0).fileIds()).containsExactly("D1", "D2");
	}

	@Test
	void photo_배열은_마지막_크기를_쓴다() {
		TelegramMessage m = new TelegramMessage(1, CHAT, null, null, null,
				List.of(new PhotoSize("S", 90, 90, 1L), new PhotoSize("L", 1280, 1280, 2L)), null);

		collector.accept(m);

		assertThat(collector.drainAll().get(0).fileIds()).containsExactly("L");
	}

	@Test
	void drainAll은_마감_전_앨범도_내보낸다() {
		collector.accept(photo(1, null, "A1", null));

		assertThat(collector.drainAll()).hasSize(1);
		assertThat(collector.drainAll()).isEmpty();
	}

	private static TelegramMessage photo(long id, String group, String fileId, String caption) {
		return new TelegramMessage(id, CHAT, null, caption, group, List.of(new PhotoSize(fileId, 1280, 960, 1000L)),
				null);
	}

	private static TelegramMessage text(long id, String text) {
		return new TelegramMessage(id, CHAT, text, null, null, null, null);
	}

	static class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
