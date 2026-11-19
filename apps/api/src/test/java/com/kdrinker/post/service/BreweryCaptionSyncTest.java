package com.kdrinker.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.kdrinker.brewery.dto.BreweryRequest;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.event.BreweryUpdatedEvent;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.brewery.service.BreweryService;
import com.kdrinker.caption.BreweryBlock;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostChannel;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostHandedOverEvent;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.telegram.PostNotifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

/** 실제 DB로 양조장 수정이 대기 게시물 캡션과 담당 채널에 미치는 영향을 확인한다. */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@RecordApplicationEvents
class BreweryCaptionSyncTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private BreweryService breweryService;
	@Autowired
	private BreweryRepository breweryRepository;
	@Autowired
	private DrinkRepository drinkRepository;
	@MockitoSpyBean
	private PostRepository postRepository;
	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ApplicationEvents applicationEvents;

	@MockitoBean
	private PostNotifier postNotifier;

	@BeforeEach
	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM telegram_notification");
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
		jdbc.update("DELETE FROM drink");
		jdbc.update("DELETE FROM brewery_alias");
		jdbc.update("DELETE FROM brewery");
	}

	private Brewery saveBrewery(String address) {
		Brewery brewery = Brewery.of("테스트양조장", "테스트양조장", BrewerySource.MANUAL);
		brewery.editManually("테스트양조장", "테스트양조장", address, null, null, null, null);
		return breweryRepository.saveAndFlush(brewery);
	}

	private Post savePost(Brewery brewery, PostStatus status, PostSource source, String caption) {
		Drink drink = drinkRepository.saveAndFlush(Drink.of("테스트술", "테스트술", brewery));
		Post post = Post.receive(source, "메모", null, CLOCK);
		ReflectionTestUtils.setField(post, "status", status);
		post.assignDrink(drink);
		post.writeCaption(caption, "#태그", null, null);
		return postRepository.saveAndFlush(post);
	}

	private static String captionWith(Brewery brewery) {
		return "훅\n\n" + BreweryBlock.render(brewery) + "\n\n#태그";
	}

	private void changeAddress(Brewery brewery, String address) {
		breweryService.update(brewery.getId(), new BreweryRequest("테스트양조장", address, null, null, null, null));
	}

	@Test
	void updatesBreweryBlockOfPendingPost() {
		Brewery brewery = saveBrewery("옛 주소");
		Post saved = savePost(brewery, PostStatus.PENDING_REVIEW, PostSource.SHARE, captionWith(brewery));
		int version = saved.getVersion();

		changeAddress(brewery, "새 주소");

		Post after = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(after.getCaption()).isEqualTo("훅\n\n" + BreweryBlock.render(breweryRepository.findById(brewery.getId()).orElseThrow())
				+ "\n\n#태그");
		assertThat(after.getCaption()).contains("📍 새 주소").doesNotContain("옛 주소").startsWith("훅\n\n").endsWith("#태그");
		assertThat(after.isCaptionEdited()).isFalse();
		assertThat(after.getVersion()).isEqualTo(version + 1);
	}

	@Test
	void handsTelegramPostOverToWeb() {
		Brewery brewery = saveBrewery("옛 주소");
		Post saved = savePost(brewery, PostStatus.PENDING_REVIEW, PostSource.TELEGRAM, captionWith(brewery));
		assertThat(saved.getChannel()).isEqualTo(PostChannel.TELEGRAM);

		changeAddress(brewery, "새 주소");

		assertThat(postRepository.findById(saved.getId()).orElseThrow().getChannel()).isEqualTo(PostChannel.WEB);
		assertThat(applicationEvents.stream(PostHandedOverEvent.class)).containsExactly(new PostHandedOverEvent(saved.getId()));
	}

	@Test
	void webPostGetsNoHandoffEvent() {
		Brewery brewery = saveBrewery("옛 주소");
		Post saved = savePost(brewery, PostStatus.PENDING_REVIEW, PostSource.SHARE, captionWith(brewery));

		changeAddress(brewery, "새 주소");

		assertThat(postRepository.findById(saved.getId()).orElseThrow().getCaption()).contains("새 주소");
		assertThat(applicationEvents.stream(PostHandedOverEvent.class)).isEmpty();
	}

	@Test
	void templateCaption_notRewritten() {
		Brewery brewery = saveBrewery("옛 주소");
		String template = "훅\n\n" + BreweryBlock.TEMPLATE + "\n\n#태그";
		Post saved = savePost(brewery, PostStatus.PENDING_REVIEW, PostSource.TELEGRAM, template);
		int version = saved.getVersion();

		changeAddress(brewery, "새 주소");

		Post after = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(after.getCaption()).isEqualTo(template);
		assertThat(after.getVersion()).isEqualTo(version + 1);
		assertThat(after.getChannel()).isEqualTo(PostChannel.WEB);
		assertThat(applicationEvents.stream(PostHandedOverEvent.class)).containsExactly(new PostHandedOverEvent(saved.getId()));
	}

	@Test
	void skipsCaptionWithBrokenMarkers() {
		Brewery brewery = saveBrewery("옛 주소");
		String broken = "훅\n\n" + BreweryBlock.MARKER + "\n📍 옛 주소\n\n#태그";
		Post saved = savePost(brewery, PostStatus.PENDING_REVIEW, PostSource.TELEGRAM, broken);

		changeAddress(brewery, "새 주소");

		Post after = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(after.getCaption()).isEqualTo(broken);
		assertThat(after.getVersion()).isEqualTo(saved.getVersion());
		assertThat(after.getChannel()).isEqualTo(PostChannel.TELEGRAM);
		assertThat(applicationEvents.stream(PostHandedOverEvent.class)).isEmpty();
	}

	@Test
	void leavesPostAloneWhenRenderedCaptionIsIdentical() {
		Brewery brewery = saveBrewery("같은 주소");
		Post saved = savePost(brewery, PostStatus.PENDING_REVIEW, PostSource.TELEGRAM, captionWith(brewery));

		// 양조장 값이 그대로여서 다시 그려도 캡션이 같다
		applicationEventPublisher.publishEvent(new BreweryUpdatedEvent(brewery.getId()));

		Post after = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(after.getCaption()).isEqualTo(saved.getCaption());
		assertThat(after.getVersion()).isEqualTo(saved.getVersion());
		assertThat(after.getChannel()).isEqualTo(PostChannel.TELEGRAM);
		assertThat(applicationEvents.stream(PostHandedOverEvent.class)).isEmpty();
	}

	@Test
	void skipsPostWhoseTransitionLostRace() {
		Brewery brewery = saveBrewery("옛 주소");
		Post saved = savePost(brewery, PostStatus.PENDING_REVIEW, PostSource.TELEGRAM, captionWith(brewery));
		doReturn(0).when(postRepository).transition(eq(saved.getId()), any(), any(), anyInt(), any());

		changeAddress(brewery, "새 주소");

		Post after = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(after.getCaption()).isEqualTo(saved.getCaption());
		assertThat(after.getChannel()).isEqualTo(PostChannel.TELEGRAM);
		assertThat(applicationEvents.stream(PostHandedOverEvent.class)).isEmpty();
	}

	@Test
	void ignoresPublishedPost() {
		Brewery brewery = saveBrewery("옛 주소");
		Post saved = savePost(brewery, PostStatus.PUBLISHED, PostSource.TELEGRAM, captionWith(brewery));
		int version = saved.getVersion();

		changeAddress(brewery, "새 주소");

		Post after = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(after.getCaption()).isEqualTo(saved.getCaption());
		assertThat(after.getVersion()).isEqualTo(version);
		assertThat(after.getChannel()).isEqualTo(PostChannel.TELEGRAM);
		assertThat(applicationEvents.stream(PostHandedOverEvent.class)).isEmpty();
	}
}
