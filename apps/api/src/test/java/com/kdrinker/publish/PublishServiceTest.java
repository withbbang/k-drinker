package com.kdrinker.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.caption.BreweryBlock;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostPublishFinishedEvent;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트 관리 트랜잭션 없이 돌려 커밋 뒤 제출이 실제로 일어나게 한다. 실행기에 넣은 작업은 PublishWorker 목 호출로 센다.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@RecordApplicationEvents
class PublishServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private PublishService service;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private DrinkRepository drinkRepository;

	@Autowired
	private BreweryRepository breweryRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationEvents events;

	@MockitoBean
	private PublishWorker worker;

	@BeforeEach
	@AfterEach
	void cleanTables() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
		jdbc.update("DELETE FROM drink");
		jdbc.update("DELETE FROM brewery");
	}

	private Post post(PostStatus status, int version, String caption) {
		Post post = Post.receive(PostSource.TELEGRAM, null, null, CLOCK);
		ReflectionTestUtils.setField(post, "status", status);
		ReflectionTestUtils.setField(post, "version", version);
		post.writeCaption(caption, "#태그", null, null);
		return postRepository.saveAndFlush(post);
	}

	private Post reload(Long id) {
		return postRepository.findById(id).orElseThrow();
	}

	@Test
	void 같은_버전으로_두_번_확정하면_한_번만_시작한다() {
		// given
		Long id = post(PostStatus.PENDING_REVIEW, 3, "캡션").getId();

		// when
		ConfirmResult first = service.confirm(id, 3);
		ConfirmResult second = service.confirm(id, 3);

		// then
		assertThat(first).isEqualTo(ConfirmResult.STARTED);
		assertThat(second).isEqualTo(ConfirmResult.STALE);
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.PUBLISHING);
		verify(worker, after(300).times(1)).publish(id);
	}

	@Test
	void 버전이_다르면_확정을_거절하고_상태를_그대로_둔다() {
		// given
		Long id = post(PostStatus.PENDING_REVIEW, 2, "캡션").getId();

		// when
		ConfirmResult result = service.confirm(id, 1);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STALE);
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		verify(worker, after(300).never()).publish(any());
	}

	@Test
	void 캡션이_2200자를_넘으면_실패로_바꾸고_실패_이벤트를_낸다() {
		// given
		Long id = post(PostStatus.PENDING_REVIEW, 1, "가".repeat(2201)).getId();

		// when
		ConfirmResult result = service.confirm(id, 1);

		// then
		assertThat(result).isEqualTo(ConfirmResult.CAPTION_TOO_LONG);
		Post after = reload(id);
		assertThat(after.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(after.getFailedStage()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(after.getFailureReason()).isEqualTo("캡션이 2,200자를 넘었습니다.");
		assertThat(events.stream(PostPublishFinishedEvent.class).toList())
				.containsExactly(new PostPublishFinishedEvent(id, PostStatus.FAILED));
		verify(worker, after(300).never()).publish(any());
	}

	@Test
	void 마커를_빼면_2200자인_캡션은_시작한다() {
		// given
		Long id = post(PostStatus.PENDING_REVIEW, 1, "가".repeat(2200) + "\n" + BreweryBlock.MARKER + "\n").getId();

		// when
		ConfirmResult result = service.confirm(id, 1);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STARTED);
		verify(worker, timeout(3000)).publish(id);
	}

	@Test
	void 길이_검사는_이름표를_채운_글_기준이다() {
		// given — 채우기 전은 2204자, 채운 뒤는 2200자
		Post post = post(PostStatus.PENDING_REVIEW, 1, "가".repeat(2192) + "\n{별점}이에요(예요)");
		post.recordRating(new java.math.BigDecimal("4"));
		postRepository.saveAndFlush(post);

		// when
		ConfirmResult result = service.confirm(post.getId(), 1);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STARTED);
		verify(worker, timeout(3000)).publish(post.getId());
	}

	@Test
	void 확정하면_양조장을_확인됨으로_표시한다() {
		// given
		Brewery brewery = breweryRepository.saveAndFlush(Brewery.of("새술양조장", "새술양조장", BrewerySource.PUBLIC_DATA));
		Drink drink = drinkRepository.saveAndFlush(Drink.of("새술", "새술", brewery));
		Post post = post(PostStatus.PENDING_REVIEW, 1, "캡션");
		post.assignDrink(drink);
		Long id = postRepository.saveAndFlush(post).getId();

		// when
		ConfirmResult result = service.confirm(id, 1);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STARTED);
		assertThat(breweryRepository.findById(brewery.getId()).orElseThrow().isVerified()).isTrue();
	}

	@Test
	void 캡션_초과로_실패한_게시물은_재시도를_거절한다() {
		// given
		Post post = post(PostStatus.FAILED, 4, "캡션");
		post.recordFailure(PostStatus.PUBLISHING, PublishFailure.CAPTION_TOO_LONG.message());
		Long id = postRepository.saveAndFlush(post).getId();

		// when
		ConfirmResult result = service.retry(id, 4);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STALE);
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.FAILED);
	}

	@Test
	void 분석_단계에서_실패한_게시물은_게시_재시도를_거절한다() {
		// given
		Post post = post(PostStatus.FAILED, 4, "캡션");
		post.recordFailure(PostStatus.ANALYZING, "분석 실패");
		Long id = postRepository.saveAndFlush(post).getId();

		// when
		ConfirmResult result = service.retry(id, 4);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STALE);
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.FAILED);
	}

	@Test
	void 게시_단계에서_실패한_게시물은_재시도로_게시_중이_된다() {
		// given
		Post post = post(PostStatus.FAILED, 4, "캡션");
		post.recordFailure(PostStatus.PUBLISHING, PublishFailure.UNKNOWN.message());
		Long id = postRepository.saveAndFlush(post).getId();

		// when
		ConfirmResult result = service.retry(id, 4);

		// then
		assertThat(result).isEqualTo(ConfirmResult.STARTED);
		Post after = reload(id);
		assertThat(after.getStatus()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(after.getFailedStage()).isNull();
		assertThat(after.getFailureReason()).isNull();
		verify(worker, timeout(3000)).publish(id);
	}

	@Test
	void 캡션이_넘치는_검토_대기_게시물은_failIfTooLong이_실패로_바꾼다() {
		// given
		Long id = post(PostStatus.PENDING_REVIEW, 1, "가".repeat(2201)).getId();
		Long ok = post(PostStatus.PENDING_REVIEW, 1, "가".repeat(2200)).getId();

		// when
		boolean over = service.failIfTooLong(id);
		boolean fine = service.failIfTooLong(ok);

		// then
		assertThat(over).isTrue();
		assertThat(fine).isFalse();
		assertThat(reload(id).getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(reload(ok).getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(events.stream(PostPublishFinishedEvent.class).toList())
				.containsExactly(new PostPublishFinishedEvent(id, PostStatus.FAILED));
		verify(worker, never()).publish(any());
	}
}
