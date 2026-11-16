package com.kdrinker.telegram;

import com.kdrinker.telegram.AlbumCollector.TextOnly;
import com.kdrinker.telegram.AlbumCollector.Unsupported;
import com.kdrinker.telegram.dto.TelegramUpdate;
import com.kdrinker.telegram.entity.NotificationKind;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 롱 폴링으로 소유자 chat의 사진·텍스트·버튼 콜백을 받는다. 스케줄러 풀이 아닌 전용 데몬 스레드 하나에서 돈다.
 * 토큰·chat id가 없으면 시작하지 않는다. 종료할 때 대기 중인 앨범은 접수하고 끝낸다.
 */
@Slf4j
@Component
public class TelegramPoller implements SmartLifecycle {

	private static final long JOIN_MILLIS = 5000;

	private final TelegramClient client;
	private final TelegramSender sender;
	private final AlbumCollector collector;
	private final TelegramIntake intake;
	private final ObjectProvider<CallbackSink> callbackSink;
	private final int pollTimeoutSeconds;
	private final PollBackoff backoff = new PollBackoff();

	private long offset;
	/** 같은 앨범의 지원하지 않는 첨부 안내를 한 번만 보내려고 마지막으로 알린 media_group_id를 기억한다(폴러 스레드만 쓴다). */
	private String lastUnsupportedGroup;
	private volatile boolean running;
	private volatile boolean stopping;
	private Thread thread;

	public TelegramPoller(TelegramClient client, TelegramSender sender, AlbumCollector collector,
			TelegramIntake intake, ObjectProvider<CallbackSink> callbackSink, TelegramProperties properties) {
		this.client = client;
		this.sender = sender;
		this.collector = collector;
		this.intake = intake;
		this.callbackSink = callbackSink;
		this.pollTimeoutSeconds = properties.pollTimeoutSeconds();
	}

	@Override
	public void start() {
		if (!client.isConfigured()) {
			log.info("텔레그램 설정이 없어 폴링을 시작하지 않습니다.");
			return;
		}
		stopping = false;
		running = true;
		thread = new Thread(this::loop, "telegram-poller");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void stop() {
		boolean wasRunning = running;
		stopping = true;
		running = false;
		if (thread != null) {
			thread.interrupt();
		}
		if (wasRunning) {
			joinPollerThread();
			intake.process(collector.drainAll());
		}
	}

	/** 폴러 스레드가 받아둔 업데이트 처리를 끝내고 멈출 때까지 잠깐 기다린다(롱 폴링 읽기 타임아웃보다 짧게). */
	private void joinPollerThread() {
		try {
			thread.join(JOIN_MILLIS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void loop() {
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				pollOnce();
				backoff.reset();
			} catch (RuntimeException e) {
				if (!running) {
					return;
				}
				Duration delay = backoff.nextDelay();
				log.warn("텔레그램 폴링 실패, {}초 뒤 다시 시도", delay.toSeconds(), e);
				try {
					Thread.sleep(delay.toMillis());
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	void pollOnce() {
		for (TelegramUpdate update : client.getUpdates(offset, pollTimeoutSeconds)) {
			if (stopping) {
				// 확인하지 않은 업데이트는 다음 기동 때 다시 받는다.
				return;
			}
			offset = Math.max(offset, update.updateId() + 1);
			try {
				handle(update);
			} catch (RuntimeException e) {
				log.warn("텔레그램 업데이트 처리 실패: update={}", update.updateId(), e);
			}
		}
	}

	private void notifyUnsupported(Unsupported unsupported) {
		String group = unsupported.mediaGroupId();
		if (group != null && group.equals(lastUnsupportedGroup)) {
			return;
		}
		lastUnsupportedGroup = group;
		sender.send(null, NotificationKind.INTAKE_FAILED, null, MessageFormatter.unsupportedAttachment());
	}

	private void handle(TelegramUpdate update) {
		if (update.message() != null) {
			if (!client.isOwnerChat(update.message().chatId())) {
				log.info("소유자가 아닌 chat의 메시지를 무시합니다.");
				return;
			}
			AlbumCollector.Accepted accepted = collector.accept(update.message());
			if (accepted instanceof TextOnly) {
				sender.send(null, NotificationKind.TEXT_ONLY, null, MessageFormatter.textOnly());
			} else if (accepted instanceof Unsupported unsupported) {
				notifyUnsupported(unsupported);
			}
		} else if (update.callbackQuery() != null) {
			if (!client.isOwnerChat(update.callbackQuery().fromChatId())) {
				log.info("소유자가 아닌 chat의 콜백을 무시합니다.");
				return;
			}
			CallbackSink sink = callbackSink.getIfAvailable();
			if (sink == null) {
				log.info("콜백 처리기가 없어 무시합니다: data={}", update.callbackQuery().data());
				return;
			}
			sink.handle(update.callbackQuery());
		}
	}

}
