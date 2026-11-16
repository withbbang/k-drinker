package com.kdrinker.telegram;

import com.kdrinker.telegram.dto.Document;
import com.kdrinker.telegram.dto.PhotoSize;
import com.kdrinker.telegram.dto.TelegramMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 사진 메시지를 앨범으로 묶는다. 같은 {@code media_group_id}는 한 앨범이고, 마지막 사진 뒤 창(3초) 안에 온 텍스트는
 * 메모로 붙는다. 폴러 스레드가 넣고 스케줄러 스레드가 꺼내므로 모든 메서드를 동기화한다.
 */
@Component
public class AlbumCollector {

	private static final Set<String> IMAGE_MIME = Set.of("image/jpeg", "image/png");

	/** 다 모인 앨범. fileSizes는 fileIds와 같은 순서이며 모르면 null 원소. */
	public record PendingAlbum(long chatId, List<String> fileIds, String memo, List<Long> fileSizes) {

		public PendingAlbum(long chatId, List<String> fileIds, String memo) {
			this(chatId, fileIds, memo, java.util.Collections.nCopies(fileIds.size(), null));
		}

	}

	public sealed interface Accepted permits Buffered, TextOnly {

	}

	/** 앨범에 붙었거나(또는 받지 않는 첨부라) 따로 알릴 일이 없는 메시지. */
	public record Buffered() implements Accepted {

	}

	/** 사진 없이 텍스트만 온 메시지. */
	public record TextOnly() implements Accepted {

	}

	private record Entry(long messageId, String fileId, Long fileSize, String caption) {

	}

	private static final class Open {

		final long chatId;
		final String mediaGroupId;
		final List<Entry> entries = new ArrayList<>();
		final List<String> texts = new ArrayList<>();
		Instant lastPhotoAt;

		Open(long chatId, String mediaGroupId) {
			this.chatId = chatId;
			this.mediaGroupId = mediaGroupId;
		}

		PendingAlbum toAlbum() {
			List<Entry> sorted = entries.stream().sorted(Comparator.comparingLong(Entry::messageId)).toList();
			List<String> memoParts = new ArrayList<>();
			for (Entry e : sorted) {
				if (e.caption() != null && !e.caption().isBlank()) {
					memoParts.add(e.caption());
				}
			}
			memoParts.addAll(texts);
			String memo = memoParts.isEmpty() ? null : String.join("\n", memoParts);
			return new PendingAlbum(chatId, sorted.stream().map(Entry::fileId).toList(), memo,
					sorted.stream().map(Entry::fileSize).toList());
		}

	}

	private final Clock clock;
	private final Duration window;
	private final Map<Long, Open> open = new HashMap<>();
	private final List<PendingAlbum> closed = new ArrayList<>();

	public AlbumCollector(Clock clock) {
		this(clock, Duration.ofSeconds(3));
	}

	@Autowired
	public AlbumCollector(Clock clock, TelegramProperties properties) {
		this(clock, properties.albumWindow());
	}

	AlbumCollector(Clock clock, Duration window) {
		this.clock = clock;
		this.window = window;
	}

	public synchronized Accepted accept(TelegramMessage m) {
		Entry photo = photoOf(m);
		Instant now = clock.instant();
		if (photo != null) {
			Open current = open.get(m.chatId());
			if (current != null && !sameGroup(current, m)) {
				closed.add(open.remove(m.chatId()).toAlbum());
				current = null;
			}
			if (current == null) {
				current = new Open(m.chatId(), m.mediaGroupId());
				open.put(m.chatId(), current);
			}
			current.entries.add(photo);
			current.lastPhotoAt = now;
			return new Buffered();
		}
		if (m.text() == null) {
			return new Buffered();
		}
		Open current = open.get(m.chatId());
		if (current != null && now.isBefore(current.lastPhotoAt.plus(window))) {
			current.texts.add(m.text());
			return new Buffered();
		}
		return new TextOnly();
	}

	/** 마지막 사진 후 창이 지난 앨범과 이미 마감된 앨범. */
	public synchronized List<PendingAlbum> drainDue() {
		Instant now = clock.instant();
		List<PendingAlbum> due = new ArrayList<>(closed);
		closed.clear();
		open.values().removeIf(o -> {
			if (!now.isBefore(o.lastPhotoAt.plus(window))) {
				due.add(o.toAlbum());
				return true;
			}
			return false;
		});
		return due;
	}

	/** 종료할 때 대기 중인 앨범을 모두 꺼낸다. */
	public synchronized List<PendingAlbum> drainAll() {
		List<PendingAlbum> all = new ArrayList<>(closed);
		closed.clear();
		open.values().forEach(o -> all.add(o.toAlbum()));
		open.clear();
		return all;
	}

	private static boolean sameGroup(Open current, TelegramMessage m) {
		return current.mediaGroupId != null && current.mediaGroupId.equals(m.mediaGroupId());
	}

	private static Entry photoOf(TelegramMessage m) {
		List<PhotoSize> sizes = m.photo();
		if (sizes != null && !sizes.isEmpty()) {
			PhotoSize largest = sizes.get(sizes.size() - 1);
			return new Entry(m.messageId(), largest.fileId(), largest.fileSize(), m.caption());
		}
		Document doc = m.document();
		if (doc != null && doc.mimeType() != null && IMAGE_MIME.contains(doc.mimeType())) {
			return new Entry(m.messageId(), doc.fileId(), doc.fileSize(), m.caption());
		}
		return null;
	}

}
