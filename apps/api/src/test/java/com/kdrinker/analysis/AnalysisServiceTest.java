package com.kdrinker.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import com.kdrinker.caption.CaptionInput;
import com.kdrinker.caption.CaptionValues;
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
import java.util.Map;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
	void DB_양조장_빈_칸_보강이_Claude_오류여도_검토_대기가_된다() {
		// given
		Brewery known = breweryRepository.save(Brewery.of("새술양조장", "새술양조장", BrewerySource.PUBLIC_DATA));
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));
		when(breweryResearcher.research(anyString(), anyString())).thenThrow(new ClaudeUnavailableException("시간 초과"));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		assertThat(reload(f.postId()).getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(breweryRepository.findById(known.getId()).orElseThrow().getResearchedAt()).isNull();
		Long linkedBreweryId = tx.execute(s -> postRepository.findById(f.postId()).orElseThrow().getDrink()
				.getBrewery().getId());
		assertThat(linkedBreweryId).isEqualTo(known.getId());
	}

	@Test
	void 검토_대기_커밋_뒤_후처리가_실패해도_검토_대기_이벤트를_낸다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));
		doAnswer(invocation -> {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					throw new IllegalStateException("디스크 가득 참");
				}
			});
			return null;
		}).when(photoService).rerender(any(), any(), any());

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		assertThat(reload(f.postId()).getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(analyzedEvents()).containsExactly(new PostAnalyzedEvent(f.postId(), PostStatus.PENDING_REVIEW));
	}

	@Test
	void 양조장이_없던_기존_술에는_이번에_찾은_양조장을_잇고_다른_정보는_둔다() {
		// given
		Drink existing = Drink.of("새술", "새술", null);
		existing.updateSpec("약주", new BigDecimal("13.0"), 375, null);
		Long drinkId = drinkRepository.save(existing).getId();
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		String breweryName = tx.execute(s -> {
			Drink d = drinkRepository.findById(drinkId).orElseThrow();
			return d.getBrewery() == null ? null : d.getBrewery().getName();
		});
		assertThat(breweryName).isEqualTo("새술양조장");
		Drink kept = drinkRepository.findById(drinkId).orElseThrow();
		assertThat(kept.getCategory()).isEqualTo("약주");
		assertThat(kept.getAbv()).isEqualByComparingTo("13.0");
		assertThat(kept.getVolumeMl()).isEqualTo(375);
	}

	@Test
	void 기존_술의_양조장은_바꾸지_않는다() {
		// given
		Brewery old = breweryRepository.save(Brewery.of("옛양조장", "옛양조장", BrewerySource.PUBLIC_DATA));
		Long drinkId = drinkRepository.save(Drink.of("새술", "새술", old)).getId();
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		Long breweryId = tx.execute(s -> drinkRepository.findById(drinkId).orElseThrow().getBrewery().getId());
		assertThat(breweryId).isEqualTo(old.getId());
	}

	/** 캡션 작성(Claude 입력·글자 수)에 넘긴 값이 게시물에 이은 술로 채울 값과 같은지. */
	private CaptionInput composedInputMatchesLinkedDrink(Long postId) {
		ArgumentCaptor<CaptionInput> input = ArgumentCaptor.forClass(CaptionInput.class);
		verify(captionWriter).write(input.capture());
		CaptionInput in = input.getValue();
		Map<String, String> filled = tx.execute(s -> {
			Post post = postRepository.findById(postId).orElseThrow();
			return CaptionValues.of(post.getDrink(), post.getRating()).asMap();
		});
		assertThat(CaptionValues.of(in.label(), in.brewery(), in.rating()).asMap()).isEqualTo(filled);
		return in;
	}

	@Test
	void 기존_술이면_캡션은_그_술의_값과_양조장으로_만든다() {
		// given: 도수·종류·용량이 비어 있고 다른 양조장에 이어진 기존 술
		Brewery old = breweryRepository.save(Brewery.of("옛양조장", "옛양조장", BrewerySource.PUBLIC_DATA));
		drinkRepository.save(Drink.of("새술", "새술", old));
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", new BigDecimal("4.5")));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		CaptionInput in = composedInputMatchesLinkedDrink(f.postId());
		assertThat(in.label().abv()).isNull();
		assertThat(in.label().category()).isNull();
		assertThat(in.label().volumeMl()).isNull();
		assertThat(in.label().tasteNotes()).isEqualTo("달콤함");
		assertThat(in.brewery().getName()).isEqualTo("옛양조장");
	}

	@Test
	void 양조장이_없던_기존_술이면_캡션은_이번에_찾은_양조장으로_만든다() {
		// given
		Drink existing = Drink.of("새술", "새술", null);
		existing.updateSpec("약주", new BigDecimal("13.0"), 375, null);
		drinkRepository.save(existing);
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		CaptionInput in = composedInputMatchesLinkedDrink(f.postId());
		assertThat(in.label().abv()).isEqualByComparingTo("13.0");
		assertThat(in.brewery().getName()).isEqualTo("새술양조장");
	}

	@Test
	void 새_술이면_캡션은_라벨_값으로_만든다() {
		// given
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		CaptionInput in = composedInputMatchesLinkedDrink(f.postId());
		assertThat(in.label().abv()).isEqualByComparingTo("6.0");
	}

	@Test
	void 이름이_같은_술이_여럿이면_새로_만들지_않고_가장_작은_id를_쓴다() {
		// given
		Long first = drinkRepository.save(Drink.of("새술", "새술", null)).getId();
		drinkRepository.save(Drink.of("새 술", "새술", null));
		Fixture f = post(PostStatus.RECEIVED, null);
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.analyze(f.postId(), PostStatus.RECEIVED);

		// then
		assertThat(drinkRepository.count()).isEqualTo(2);
		Long linked = tx.execute(s -> postRepository.findById(f.postId()).orElseThrow().getDrink().getId());
		assertThat(linked).isEqualTo(first);
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

	@Test
	void 재시도_시작은_버전이_맞으면_바로_분석_중으로_바꾸고_실패_정보를_지운다() {
		// given
		Fixture f = post(PostStatus.FAILED, null);
		int version = reload(f.postId()).getVersion();

		// when
		boolean started = analysisService.startRetry(f.postId(), version);

		// then
		assertThat(started).isTrue();
		Post post = reload(f.postId());
		assertThat(post.getStatus()).isEqualTo(PostStatus.ANALYZING);
		assertThat(post.getVersion()).isEqualTo(version + 1);
		assertThat(post.getFailedStage()).isNull();
		assertThat(post.getFailureReason()).isNull();
		verifyNoInteractions(labelReader);
	}

	@Test
	void 같은_버전으로_재시도를_두_번_시작하면_두_번째는_거절된다() {
		// given
		Fixture f = post(PostStatus.FAILED, null);
		int version = reload(f.postId()).getVersion();
		analysisService.startRetry(f.postId(), version);

		// when
		boolean second = analysisService.startRetry(f.postId(), version);

		// then
		assertThat(second).isFalse();
		assertThat(reload(f.postId()).getVersion()).isEqualTo(version + 1);
	}

	@Test
	void 재시도_시작은_버전이_다르거나_게시_단계_실패면_아무것도_바꾸지_않는다() {
		// given
		Fixture f = post(PostStatus.FAILED, null);
		int version = reload(f.postId()).getVersion();
		Fixture published = post(PostStatus.FAILED, null);
		jdbc.update("UPDATE post SET failed_stage = 'PUBLISHING' WHERE id = ?", published.postId());
		int publishedVersion = reload(published.postId()).getVersion();

		// when
		boolean wrongVersion = analysisService.startRetry(f.postId(), version + 1);
		boolean wrongStage = analysisService.startRetry(published.postId(), publishedVersion);

		// then
		assertThat(wrongVersion).isFalse();
		assertThat(wrongStage).isFalse();
		assertThat(reload(f.postId()).getStatus()).isEqualTo(PostStatus.FAILED);
		assertThat(reload(f.postId()).getFailureReason()).isEqualTo("이전 실패");
		assertThat(reload(published.postId()).getStatus()).isEqualTo(PostStatus.FAILED);
	}

	@Test
	void 재시도로_분석_중이_된_게시물을_이어서_분석하면_검토_대기가_되고_이벤트를_낸다() {
		// given
		Fixture f = post(PostStatus.FAILED, null);
		analysisService.startRetry(f.postId(), reload(f.postId()).getVersion());
		givenPortsSucceed(label("새술", null));

		// when
		analysisService.resume(f.postId());

		// then
		assertThat(reload(f.postId()).getStatus()).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(analyzedEvents()).containsExactly(new PostAnalyzedEvent(f.postId(), PostStatus.PENDING_REVIEW));
	}

	@Test
	void 분석_중이_아닌_게시물은_이어서_분석하지_않는다() {
		// given
		Fixture f = post(PostStatus.FAILED, null);

		// when
		analysisService.resume(f.postId());

		// then
		assertThat(reload(f.postId()).getStatus()).isEqualTo(PostStatus.FAILED);
		verifyNoInteractions(labelReader);
		assertThat(analyzedEvents()).isEmpty();
	}
}
