package com.kdrinker.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kdrinker.analysis.brewery.BreweryResearcher;
import com.kdrinker.analysis.brewery.ResearchedBrewery;
import com.kdrinker.analysis.claude.ClaudeUnavailableException;
import com.kdrinker.analysis.label.LabelReader;
import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.analysis.label.PhotoBox;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.caption.BreweryBlock;
import com.kdrinker.caption.CaptionDraft;
import com.kdrinker.caption.CaptionWriter;
import com.kdrinker.caption.DraftTag;
import com.kdrinker.caption.Hashtags;
import com.kdrinker.caption.TagCategory;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import com.kdrinker.media.FocusBox;
import com.kdrinker.media.service.PhotoService;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostAnalyzedEvent;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 포트(Claude 경계)와 PhotoService만 목으로 두고 BreweryMatcher·CaptionService·ClaudeImages는 실제로 돌린다.
 * 게시물은 저장소로 직접 넣으므로 PostReceivedEvent가 나지 않아 비동기 리스너는 돌지 않는다. analyze를 직접 부른다.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@RecordApplicationEvents
class AnalysisServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneOffset.UTC);
	private static final Path MEDIA_DIR = createMediaDir();

	@DynamicPropertySource
	static void mediaDir(DynamicPropertyRegistry registry) {
		registry.add("app.media.dir", MEDIA_DIR::toString);
	}

	@Autowired
	private AnalysisService analysisService;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostPhotoRepository postPhotoRepository;

	@Autowired
	private DrinkRepository drinkRepository;

	@Autowired
	private BreweryRepository breweryRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate tx;

	@Autowired
	private ApplicationEvents events;

	@MockitoBean
	private LabelReader labelReader;

	@MockitoBean
	private BreweryResearcher breweryResearcher;

	@MockitoBean
	private CaptionWriter captionWriter;

	@MockitoBean
	private PhotoService photoService;

	private static Path createMediaDir() {
		try {
			return Files.createTempDirectory("analysis-media");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@BeforeEach
	@AfterEach
	void cleanTables() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
		jdbc.update("DELETE FROM drink");
		jdbc.update("DELETE FROM brewery_alias");
		jdbc.update("DELETE FROM brewery");
	}

	// --- 픽스처 ---

	private record Fixture(Long postId, Long photo0, Long photo1) {
	}

	/** 사진 2장(실제 JPEG 원본 파일)을 가진 게시물. */
	private Fixture post(PostStatus status, BigDecimal rating) {
		Post post = Post.receive(PostSource.TELEGRAM, "달달하고 전이랑 잘 맞음", rating, CLOCK);
		ReflectionTestUtils.setField(post, "status", status);
		if (status == PostStatus.FAILED) {
			post.recordFailure(PostStatus.ANALYZING, "이전 실패");
		}
		Long postId = postRepository.saveAndFlush(post).getId();
		Long photo0 = photo(post, 0);
		Long photo1 = photo(post, 1);
		return new Fixture(postId, photo0, photo1);
	}

	private Long photo(Post post, int position) {
		String relative = "original/" + post.getId() + "/" + position + ".jpg";
		writeJpeg(MEDIA_DIR.resolve(relative));
		String token = ("t" + post.getId() + "p" + position + "x".repeat(43)).substring(0, 43);
		return postPhotoRepository.saveAndFlush(PostPhoto.of(post, position, relative, token, FitMode.CROP)).getId();
	}

	private static void writeJpeg(Path target) {
		try {
			Files.createDirectories(target.getParent());
			ImageIO.write(new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), "jpg", target.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static LabelResult label(String drinkName, BigDecimal rating) {
		return new LabelResult(drinkName, "막걸리", new BigDecimal("6.0"), 750, "새술양조장", rating, "달콤함", "전",
				List.of(new PhotoBox(0, new FocusBox(0.1, 0.1, 0.5, 0.8))));
	}

	private void givenPortsSucceed(LabelResult label) {
		when(labelReader.read(anyList(), any())).thenReturn(label);
		when(breweryResearcher.research(anyString(), anyString())).thenReturn(Optional.of(new ResearchedBrewery(
				"새술양조장", "경기도 여주시 1", "031-000-0000", "https://sool.example.com", "@sool", "https://sool.example.com")));
		when(captionWriter.write(any())).thenReturn(new CaptionDraft("오늘의 한 잔", "새술을 마셨어요.", "달콤했어요.",
				List.of(new DraftTag("#새술", TagCategory.NAME))));
	}

	private Post reload(Long id) {
		return postRepository.findById(id).orElseThrow();
	}

	private String drinkNameOf(Long postId) {
		return tx.execute(s -> {
			Drink drink = postRepository.findById(postId).orElseThrow().getDrink();
			return drink == null ? null : drink.getName();
		});
	}

	private List<PostAnalyzedEvent> analyzedEvents() {
		return events.stream(PostAnalyzedEvent.class).toList();
	}

	// --- 테스트 ---

	@Test
	void 정상_분석이면_검토_대기가_되고_술_캡션_상자_렌더링이_반영된다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", new BigDecimal("4.5")));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(post.getVersion()).isGreaterThanOrEqualTo(1);
		assertThat(drinkNameOf(f.postId())).isEqualTo("새술");
		assertThat(post.getCaption().split(BreweryBlock.MARKER, -1)).hasSize(3);
		assertThat(post.getCaption()).containsOnlyOnce("#전통주후기");
		assertThat(post.getHashtags()).startsWith(String.join(" ", Hashtags.FIXED));
		assertThat(post.getTasteNotes()).isEqualTo("달콤함");
		assertThat(post.getPairing()).isEqualTo("전");
		verify(photoService).rerender(eq(f.photo0()), any(), eq(FitMode.CROP));
		verify(photoService, never()).rerender(eq(f.photo1()), any(), any());
		assertThat(analyzedEvents()).containsExactly(new PostAnalyzedEvent(f.postId(), PostStatus.PENDING_REVIEW));
	}

	@Test
	void 사진은_순서대로_한_장씩_준비해_라벨_리더에_넘긴다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<byte[]>> images = ArgumentCaptor.forClass(List.class);
		verify(labelReader).read(images.capture(), eq("달달하고 전이랑 잘 맞음"));
		assertThat(images.getValue()).hasSize(2).allSatisfy(b -> assertThat(b).isNotEmpty());
	}

	@Test
	void 접수_별점이_라벨_별점보다_우선한다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, new BigDecimal("4.0"));
		givenPortsSucceed(label("새술", new BigDecimal("4.5")));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		assertThat(reload(f.postId()).getRating()).isEqualByComparingTo("4.0");
	}

	@Test
	void 접수_별점이_없으면_라벨_별점을_쓴다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", new BigDecimal("4.5")));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		assertThat(reload(f.postId()).getRating()).isEqualByComparingTo("4.5");
	}

	@Test
	void 이미_있는_술은_기존_정보를_덮어쓰지_않는다() {
		// given
		Brewery brewery = breweryRepository.save(Brewery.of("옛양조장", "옛양조장", BrewerySource.PUBLIC_DATA));
		Drink existing = Drink.of("새술", "새술", brewery);
		existing.updateSpec("약주", new BigDecimal("13.0"), 375, "새 술,새술한잔");
		Long drinkId = drinkRepository.save(existing).getId();
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Drink kept = drinkRepository.findById(drinkId).orElseThrow();
		assertThat(kept.getAliases()).isEqualTo("새 술,새술한잔");
		assertThat(kept.getCategory()).isEqualTo("약주");
		assertThat(drinkRepository.count()).isEqualTo(1);
		Long linkedDrinkId = tx.execute(s -> postRepository.findById(f.postId()).orElseThrow().getDrink().getId());
		assertThat(linkedDrinkId)
				.isEqualTo(drinkId);
	}

	@Test
	void 새_술은_라벨_정보로_만든다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Drink created = drinkRepository.findAll().get(0);
		assertThat(created.getNormalizedName()).isEqualTo("새술");
		assertThat(created.getCategory()).isEqualTo("막걸리");
		assertThat(created.getAbv()).isEqualByComparingTo("6.0");
		assertThat(created.getVolumeMl()).isEqualTo(750);
		assertThat(created.getAliases()).isNull();
	}

	@Test
	void 술_이름을_못_읽으면_실패하고_사유를_남긴다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label(null, null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(post.getFailedStage()).isEqualTo(PostStatus.ANALYZING);
		assertThat(post.getFailureReason()).isEqualTo("사진에서 술 이름을 읽지 못했습니다.");
		verifyNoInteractions(captionWriter, photoService);
		assertThat(analyzedEvents()).containsExactly(new PostAnalyzedEvent(f.postId(), PostStatus.FAILED));
	}

	@Test
	void 라벨_리더가_Claude_오류면_분석_서버_사유로_실패한다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		when(labelReader.read(anyList(), any())).thenThrow(new ClaudeUnavailableException("거절"));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(post.getFailureReason()).isEqualTo("분석 서버 응답에 문제가 있었습니다.");
	}

	@Test
	void 양조장_조사가_Claude_오류면_분석_서버_사유로_실패한다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));
		when(breweryResearcher.research(anyString(), anyString())).thenThrow(new ClaudeUnavailableException("시간 초과"));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(post.getFailureReason()).isEqualTo("분석 서버 응답에 문제가 있었습니다.");
		assertThat(analyzedEvents()).containsExactly(new PostAnalyzedEvent(f.postId(), PostStatus.FAILED));
	}

	@Test
	void 예상하지_못한_예외면_알_수_없는_오류로_실패한다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));
		when(captionWriter.write(any())).thenThrow(new IllegalStateException("버그"));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(post.getFailureReason()).isEqualTo("알 수 없는 오류가 났습니다. 서버 로그를 확인해 주세요.");
		assertThat(drinkRepository.count()).isZero();
	}

	@Test
	void 실패한_게시물을_다시_분석하면_실패_정보가_지워진다() {
		// given
		Fixture f = post(PostStatus.FAILED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.FAILED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(post.getFailedStage()).isNull();
		assertThat(post.getFailureReason()).isNull();
	}

	@Test
	void 상태가_맞지_않으면_아무것도_하지_않는다() {
		// given
		Fixture f = post(PostStatus.PENDING_REVIEW, null);
		int versionBefore = reload(f.postId()).getVersion();

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(post.getVersion()).isEqualTo(versionBefore);
		verifyNoInteractions(labelReader, breweryResearcher, captionWriter, photoService);
		assertThat(analyzedEvents()).isEmpty();
	}

	@Test
	void 실패_게시물에_최초_분석을_요청하면_실패_정보를_지우지_않는다() {
		// given
		Fixture f = post(PostStatus.FAILED, null);

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(post.getFailureReason()).isEqualTo("이전 실패");
		verifyNoInteractions(labelReader);
	}

	@Test
	void 게시_단계에서_실패한_게시물은_다시_분석하지_않는다() {
		// given
		Fixture f = post(PostStatus.FAILED, null);
		jdbc.update("UPDATE post SET failed_stage = 'PUBLISHING', failure_reason = '게시 실패' WHERE id = ?", f.postId());
		int versionBefore = reload(f.postId()).getVersion();

		// when
		analysisService.analyze(f.postId(), PostStatus.FAILED);

		// then
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(post.getFailedStage()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(post.getFailureReason()).isEqualTo("게시 실패");
		assertThat(post.getVersion()).isEqualTo(versionBefore);
		verifyNoInteractions(labelReader, breweryResearcher, captionWriter, photoService);
		assertThat(analyzedEvents()).isEmpty();
	}

	@Test
	void 트랜잭션_안에서_분석을_부르면_호출_버그로_보고_예외를_던진다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);

		// when / then
		assertThatThrownBy(() -> tx.executeWithoutResult(s -> analysisService.analyze(f.postId(), PostStatus.RECEIVED)))
				.isInstanceOf(IllegalStateException.class);
		assertThat(reload(f.postId()).getStatus()).isEqualTo(PostStatus.RECEIVED);
		verifyNoInteractions(labelReader);
	}
}
