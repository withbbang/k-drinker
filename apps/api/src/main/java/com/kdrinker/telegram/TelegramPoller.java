package com.kdrinker.telegram;

import com.kdrinker.telegram.AlbumCollector.TextOnly;
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

	private final TelegramClient client;
	private final TelegramSender sender;
	private final AlbumCollector collector;
	private final TelegramIntake intake;
	private final ObjectProvider<CallbackSink> callbackSink;
	private final int pollTimeoutSeconds;
	private final PollBackoff backoff = new PollBackoff();

	private long offset;
	private volatile boolean running;
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
		running = true;
		thread = new Thread(this::loop, "telegram-poller");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void stop() {
		boolean wasRunning = running;
		running = false;
		if (thread != null) {
			thread.interrupt();
		}
		if (wasRunning) {
			intake.process(collector.drainAll());
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
				log.warn("텔레그램 폴링 실패, {}초 뒤 다시 시도: {}", delay.toSeconds(), e.getMessage());
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
			offset = Math.max(offset, update.updateId() + 1);
			try {
				handle(update);
			} catch (RuntimeException e) {
				log.warn("텔레그램 업데이트 처리 실패: update={}, {}", update.updateId(), e.getMessage());
			}
		}
	}

	private void handle(TelegramUpdate update) {
		if (update.message() != null) {
			if (!client.isOwnerChat(update.message().chatId())) {
				log.info("소유자가 아닌 chat의 메시지를 무시합니다.");
				return;
			}
			if (collector.accept(update.message()) instanceof TextOnly) {
				sender.send(null, NotificationKind.TEXT_ONLY, null, MessageFormatter.textOnly());
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
