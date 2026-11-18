package com.kdrinker.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kdrinker.analysis.claude.ClaudeUnavailableException;
import com.kdrinker.brewery.dto.BreweryRequest;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.brewery.service.BreweryService;
import com.kdrinker.caption.BreweryBlock;
import com.kdrinker.caption.CaptionAssembler;
import com.kdrinker.caption.CaptionInput;
import com.kdrinker.caption.CaptionParts;
import com.kdrinker.caption.CaptionService;
import com.kdrinker.caption.ComposedCaption;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import com.kdrinker.media.FocusBox;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.media.service.PhotoService;
import com.kdrinker.post.dto.PostUpdateRequest;
import com.kdrinker.post.dto.PostUpdateRequest.PhotoEdit;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostChannel;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostEditedEvent;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PostEditServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC);
	private static final Long POST_ID = 1L;
	private static final int VERSION = 3;

	private final PostRepository postRepository = mock(PostRepository.class);
	private final PostPhotoRepository postPhotoRepository = mock(PostPhotoRepository.class);
	private final DrinkRepository drinkRepository = mock(DrinkRepository.class);
	private final BreweryRepository breweryRepository = mock(BreweryRepository.class);
	private final CaptionService captionService = mock(CaptionService.class);
	private final PhotoService photoService = mock(PhotoService.class);
	private final PhotoStorage photoStorage = mock(PhotoStorage.class);
	private final PostQueryService postQueryService = mock(PostQueryService.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
	private final PostEditService service = new PostEditService(postRepository, postPhotoRepository,
			drinkRepository, breweryRepository, new BreweryService(breweryRepository), captionService, photoService,
			photoStorage, postQueryService, events, CLOCK, transactionManager);

	private Brewery kooksoondang;
	private Brewery hwayo;
	private Drink drink;
	private Post post;
	private PostPhoto photo1;
	private PostPhoto photo2;

	/** 목 트랜잭션 관리자: 시작할 때 동기화를 켜고, 커밋이면 afterCommit을 돌리고, 롤백이면 그냥 버린다. */
	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any())).thenAnswer(inv -> {
			TransactionSynchronizationManager.initSynchronization();
			return new SimpleTransactionStatus();
		});
		doAnswer(inv -> {
			TransactionStatus status = inv.getArgument(0);
			List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
			TransactionSynchronizationManager.clearSynchronization();
			if (!status.isRollbackOnly()) {
				syncs.forEach(TransactionSynchronization::afterCommit);
			}
			return null;
		}).when(transactionManager).commit(any());
		doAnswer(inv -> {
			TransactionSynchronizationManager.clearSynchronization();
			return null;
		}).when(transactionManager).rollback(any());

		kooksoondang = brewery(10L, "국순당");
		hwayo = brewery(11L, "화요");
		drink = Drink.of("막걸리", "막걸리", kooksoondang);
		ReflectionTestUtils.setField(drink, "id", 20L);
		post = post(PostStatus.PENDING_REVIEW);
		photo1 = photo(100L, 0, FitMode.CROP);
		photo2 = photo(101L, 1, FitMode.CROP);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(postPhotoRepository.findByPostIdOrderByPositionAsc(POST_ID)).thenReturn(List.of(photo1, photo2));
		when(breweryRepository.findById(10L)).thenReturn(Optional.of(kooksoondang));
		when(breweryRepository.findById(11L)).thenReturn(Optional.of(hwayo));
		when(drinkRepository.findById(20L)).thenReturn(Optional.of(drink));
		when(postRepository.transition(eq(POST_ID), any(), eq(PostStatus.PENDING_REVIEW), eq(VERSION), any()))
				.thenReturn(1);
	}

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private static Brewery brewery(Long id, String name) {
		Brewery b = Brewery.of(name, name, BrewerySource.PUBLIC_DATA);
		ReflectionTestUtils.setField(b, "id", id);
		return b;
	}

	private Post post(PostStatus status) {
		Post p = Post.receive(PostSource.SHARE, "메모", new BigDecimal("4.0"), CLOCK);
		ReflectionTestUtils.setField(p, "id", POST_ID);
		ReflectionTestUtils.setField(p, "status", status);
		ReflectionTestUtils.setField(p, "version", VERSION);
		p.assignDrink(drink);
		p.writeCaption("훅\n\n★4.0\n\n" + BreweryBlock.render(kooksoondang) + "\n\n#막걸리", "#막걸리", "달다", "전");
		return p;
	}

	private PostPhoto photo(Long id, int position, FitMode mode) {
		PostPhoto p = PostPhoto.of(post, position, "original/1/" + position + ".jpg", "t" + id, mode);
		ReflectionTestUtils.setField(p, "id", id);
		p.attachRendered("rendered/t" + id + ".jpg");
		return p;
	}

	private PostUpdateRequest request(Long breweryId, BreweryRequest edit, String head, List<PhotoEdit> photos) {
		return new PostUpdateRequest(VERSION, null, null, breweryId, edit, new BigDecimal("4.0"), "메모", head,
				"#막걸리", photos);
	}

	private PostUpdateRequest unchanged() {
		return request(null, null, "훅\n\n★4.0", keepPhotos());
	}

	private static List<PhotoEdit> keepPhotos() {
		return List.of(new PhotoEdit(100L, FitMode.CROP), new PhotoEdit(101L, FitMode.CROP));
	}

	private static ErrorCode errorOf(Runnable action) {
		try {
			action.run();
		} catch (BusinessException e) {
			return e.getErrorCode();
		}
		throw new AssertionError("BusinessException이 나지 않았습니다.");
	}

	@Test
	void 저장하면_PENDING_REVIEW로_전이하고_이벤트를_낸다() {
		// given
		PostUpdateRequest req = unchanged();

		// when
		service.update(POST_ID, req);

		// then
		verify(postRepository).transition(eq(POST_ID), eq(PostStatus.PENDING_REVIEW), eq(PostStatus.PENDING_REVIEW),
				eq(VERSION), any());
		verify(events).publishEvent(new PostEditedEvent(POST_ID, false));
		verify(postQueryService).detail(POST_ID);
	}

	@Test
	void 텔레그램_담당_게시물을_저장하면_웹_담당이_되고_넘겨받았다는_이벤트를_낸다() {
		// given
		ReflectionTestUtils.setField(post, "channel", PostChannel.TELEGRAM);

		// when
		service.update(POST_ID, unchanged());

		// then
		assertThat(post.getChannel()).isEqualTo(PostChannel.WEB);
		verify(events).publishEvent(new PostEditedEvent(POST_ID, true));
	}

	@Test
	void 이미_웹_담당인_게시물을_저장하면_넘겨받지_않은_이벤트를_낸다() {
		// given
		assertThat(post.getChannel()).isEqualTo(PostChannel.WEB);

		// when
		service.update(POST_ID, unchanged());

		// then
		assertThat(post.getChannel()).isEqualTo(PostChannel.WEB);
		verify(events).publishEvent(new PostEditedEvent(POST_ID, false));
	}

	@Test
	void 버전_충돌로_저장이_실패하면_이벤트를_내지_않는다() {
		// given
		ReflectionTestUtils.setField(post, "channel", PostChannel.TELEGRAM);
		when(postRepository.transition(eq(POST_ID), any(), eq(PostStatus.PENDING_REVIEW), eq(VERSION), any()))
				.thenReturn(0);

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, unchanged()));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_VERSION_CONFLICT);
		verify(events, never()).publishEvent(any());
	}

	@Test
	void 실패_게시물을_저장하면_실패_정보를_지우고_검토_대기로_돌린다() {
		// given
		post = post(PostStatus.FAILED);
		post.recordFailure(PostStatus.ANALYZING, "라벨을 못 읽음");
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		// when
		service.update(POST_ID, unchanged());

		// then
		assertThat(post.getFailedStage()).isNull();
		assertThat(post.getFailureReason()).isNull();
		verify(postRepository).transition(eq(POST_ID), eq(PostStatus.FAILED), eq(PostStatus.PENDING_REVIEW),
				eq(VERSION), any());
	}

	@Test
	void 저장_직전에_버전이_바뀌면_409이고_아무것도_바뀌지_않는다() {
		// given: 1번 사진을 빼서 저장하는데, [확인]이 먼저 버전을 올려 전이가 0건이다
		when(postRepository.transition(eq(POST_ID), any(), eq(PostStatus.PENDING_REVIEW), eq(VERSION), any()))
				.thenReturn(0);
		PostUpdateRequest req = request(null, null, "훅\n\n★4.0", List.of(new PhotoEdit(101L, FitMode.CROP)));

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_VERSION_CONFLICT);
		verify(events, never()).publishEvent(any());
		verify(photoStorage, never()).deleteQuietly(anyString());
		verify(transactionManager, times(1)).rollback(any());
	}

	@Test
	void 읽은_뒤_버전이_바뀌었으면_쓰기_전에_409다() {
		// given: 두 번째 조회(쓰기 트랜잭션)에서 버전이 올라가 있다
		Post newer = post(PostStatus.PENDING_REVIEW);
		ReflectionTestUtils.setField(newer, "version", VERSION + 1);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post), Optional.of(newer));

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, unchanged()));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_VERSION_CONFLICT);
		verify(postRepository, never()).transition(any(), any(), any(), anyInt(), any());
	}

	@Test
	void 요청_버전이_다르면_409다() {
		// given
		PostUpdateRequest req = new PostUpdateRequest(VERSION - 1, null, null, null, null, null, null, "훅", "",
				keepPhotos());

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_VERSION_CONFLICT);
	}

	@Test
	void 없는_게시물은_404다() {
		// given
		when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, unchanged()));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}

	@Test
	void 컨테이너가_있는_실패_게시물은_수정할_수_없다() {
		// given
		post = post(PostStatus.FAILED);
		post.recordContainer("container-1");
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, unchanged()));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_NOT_EDITABLE);
		verify(postRepository, never()).transition(any(), any(), any(), anyInt(), any());
	}

	@Test
	void PUBLISHED는_수정할_수_없다() {
		// given
		post = post(PostStatus.PUBLISHED);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, unchanged()));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_NOT_EDITABLE);
	}

	@Test
	void 직접_고친_캡션은_양조장_블록만_바뀐다() {
		// given
		PostUpdateRequest req = request(11L, null, "내가 고친 훅", keepPhotos());

		// when
		service.update(POST_ID, req);

		// then
		verifyNoInteractions(captionService);
		assertThat(post.getCaption()).isEqualTo(new CaptionParts("내가 고친 훅", "#막걸리").join(hwayo));
		assertThat(post.isCaptionEdited()).isTrue();
		assertThat(post.getHashtags()).isEqualTo("#막걸리");
		assertThat(drink.getBrewery()).isSameAs(hwayo);
	}

	@Test
	void 캡션을_안_고치고_양조장을_바꾸면_다시_생성한다() {
		// given
		String regenerated = "새 훅\n\n" + BreweryBlock.render(hwayo) + "\n\n#화요";
		when(captionService.compose(any())).thenReturn(new ComposedCaption(regenerated, "#화요"));

		// when
		service.update(POST_ID, request(11L, null, "훅\n\n★4.0", keepPhotos()));

		// then
		ArgumentCaptor<CaptionInput> input = ArgumentCaptor.forClass(CaptionInput.class);
		verify(captionService).compose(input.capture());
		assertThat(input.getValue().label().drinkName()).isEqualTo("막걸리");
		assertThat(input.getValue().label().breweryName()).isEqualTo("화요");
		assertThat(input.getValue().label().tasteNotes()).isEqualTo("달다");
		assertThat(input.getValue().label().boxes()).isEmpty();
		assertThat(input.getValue().brewery().getName()).isEqualTo("화요");
		assertThat(post.getCaption()).isEqualTo(regenerated);
		assertThat(post.getHashtags()).isEqualTo("#화요");
		assertThat(post.isCaptionEdited()).isFalse();
	}

	@Test
	void 양조장_정보를_고치면_캡션_블록과_레코드에_같은_값이_들어간다() {
		// given
		BreweryRequest edit = new BreweryRequest("국순당", "서울 강남구", null, null, "@ksd", null);
		when(captionService.compose(any())).thenAnswer(inv -> {
			CaptionInput in = inv.getArgument(0);
			return new ComposedCaption("훅\n\n" + BreweryBlock.render(in.brewery()), "");
		});

		// when
		service.update(POST_ID, request(null, edit, "훅\n\n★4.0", keepPhotos()));

		// then
		assertThat(kooksoondang.getAddress()).isEqualTo("서울 강남구");
		assertThat(kooksoondang.getInstagramHandle()).isEqualTo("ksd");
		assertThat(post.getCaption()).isEqualTo("훅\n\n" + BreweryBlock.render(kooksoondang));
	}

	@Test
	void 재생성이_실패하면_양조장_블록만_바꿔_저장한다() {
		// given
		when(captionService.compose(any())).thenThrow(new ClaudeUnavailableException("down"));

		// when
		service.update(POST_ID, request(11L, null, "훅\n\n★4.0", keepPhotos()));

		// then
		assertThat(post.getCaption()).isEqualTo(new CaptionParts("훅\n\n★4.0", "#막걸리").join(hwayo));
		assertThat(post.isCaptionEdited()).isFalse();
		verify(events).publishEvent(new PostEditedEvent(POST_ID, false));
	}

	@Test
	void 사진을_모두_빼면_400이다() {
		// given
		PostUpdateRequest req = request(null, null, "훅\n\n★4.0", List.of());

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_PHOTO_COUNT);
		verify(postPhotoRepository, never()).delete(any());
		verify(photoStorage, never()).deleteQuietly(anyString());
	}

	@Test
	void 다른_게시물의_사진_id는_400이다() {
		// given
		PostUpdateRequest req = request(null, null, "훅\n\n★4.0",
				List.of(new PhotoEdit(100L, FitMode.CROP), new PhotoEdit(999L, FitMode.CROP)));

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
		verify(postPhotoRepository, never()).delete(any());
		verify(photoStorage, never()).deleteQuietly(anyString());
	}

	@Test
	void 같은_사진_id를_두_번_보내면_400이다() {
		// given
		PostUpdateRequest req = request(null, null, "훅\n\n★4.0",
				List.of(new PhotoEdit(100L, FitMode.CROP), new PhotoEdit(100L, FitMode.CROP)));

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
	}

	@Test
	void 빠진_사진은_커밋_뒤에_원본과_렌더링_파일을_지운다() {
		// given
		PostUpdateRequest req = request(null, null, "훅\n\n★4.0", List.of(new PhotoEdit(101L, FitMode.CROP)));

		// when
		service.update(POST_ID, req);

		// then
		verify(postPhotoRepository).delete(photo1);
		verify(photoStorage).deleteQuietly("original/1/0.jpg");
		verify(photoStorage).deleteQuietly("rendered/t100.jpg");
		assertThat(photo2.getPosition()).isZero();
	}

	@Test
	void 여백_넣기로_바꾼_사진만_다시_렌더링한다() {
		// given
		photo2.changeFocus(0.1, 0.2, 0.3, 0.4);
		PostUpdateRequest req = request(null, null, "훅\n\n★4.0",
				List.of(new PhotoEdit(101L, FitMode.PAD), new PhotoEdit(100L, FitMode.CROP)));

		// when
		service.update(POST_ID, req);

		// then
		verify(photoService, times(1)).rerender(any(), any(), any());
		verify(photoService).rerender(101L, new FocusBox(0.1, 0.2, 0.3, 0.4), FitMode.PAD);
		assertThat(photo2.getPosition()).isZero();
		assertThat(photo1.getPosition()).isEqualTo(1);
	}

	@Test
	void 캡션이_2200자를_넘으면_400이다() {
		// given: 머리 글자 수를 조절해 전체를 2,201자로 맞춘다
		int base = CaptionAssembler.length(new CaptionParts("가", "#막걸리").join(kooksoondang));
		String head = "가".repeat(2201 - base + 1);
		PostUpdateRequest req = request(null, null, head, keepPhotos());

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_CAPTION_TOO_LONG);
		verify(events, never()).publishEvent(any());
	}

	@Test
	void 캡션이_2200자면_저장된다() {
		// given
		int base = CaptionAssembler.length(new CaptionParts("가", "#막걸리").join(kooksoondang));
		String head = "가".repeat(2200 - base + 1);
		PostUpdateRequest req = request(null, null, head, keepPhotos());

		// when
		service.update(POST_ID, req);

		// then
		assertThat(CaptionAssembler.length(post.getCaption())).isEqualTo(2200);
		verify(events).publishEvent(new PostEditedEvent(POST_ID, false));
	}

	@Test
	void drinkId와_newDrinkName을_함께_보내면_400이다() {
		// given
		PostUpdateRequest req = new PostUpdateRequest(VERSION, 20L, "새 술", null, null, null, null, "훅", "",
				keepPhotos());

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
	}

	@Test
	void 새_술_이름이_비면_400이다() {
		// given
		PostUpdateRequest req = new PostUpdateRequest(VERSION, null, " ( ) ", null, null, null, null, "훅", "",
				keepPhotos());

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
	}

	@Test
	void 없는_양조장_id는_400이다() {
		// given
		PostUpdateRequest req = request(999L, null, "훅\n\n★4.0", keepPhotos());

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
	}

	@Test
	void 새_술_이름이면_현재_양조장으로_술을_만든다() {
		// given
		when(drinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(breweryRepository.getReferenceById(10L)).thenReturn(kooksoondang);
		when(captionService.compose(any())).thenReturn(new ComposedCaption("새 캡션", ""));
		PostUpdateRequest req = new PostUpdateRequest(VERSION, null, " 생막걸리 ", null, null, new BigDecimal("4.0"),
				"메모", "훅\n\n★4.0", "#막걸리", keepPhotos());

		// when
		service.update(POST_ID, req);

		// then
		assertThat(post.getDrink().getName()).isEqualTo("생막걸리");
		assertThat(post.getDrink().getNormalizedName()).isEqualTo("생막걸리");
		assertThat(post.getDrink().getBrewery()).isSameAs(kooksoondang);
		verify(captionService).compose(any());
	}

	@Test
	void 별점과_메모를_비우면_지운다() {
		// given
		PostUpdateRequest req = new PostUpdateRequest(VERSION, null, null, null, null, null, null, "훅\n\n★4.0",
				"#막걸리", keepPhotos());

		// when
		service.update(POST_ID, req);

		// then
		assertThat(post.getRating()).isNull();
		assertThat(post.getMemo()).isNull();
	}

	@Test
	void 별점이_0_5_단위가_아니면_400이다() {
		// given
		PostUpdateRequest req = new PostUpdateRequest(VERSION, null, null, null, null, new BigDecimal("4.3"), null,
				"훅", "", keepPhotos());

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, req));

		// then
		assertThat(code).isEqualTo(ErrorCode.POST_INVALID_RATING);
	}

	@Test
	void 트랜잭션_안에서_부르면_예외다() {
		// given
		TransactionSynchronizationManager.setActualTransactionActive(true);
		try {
			// when / then
			assertThatThrownBy(() -> service.update(POST_ID, unchanged())).isInstanceOf(IllegalStateException.class);
		} finally {
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}

	private PostUpdateRequest drinkChange(Long drinkId, BreweryRequest edit) {
		return new PostUpdateRequest(VERSION, drinkId, null, null, edit, new BigDecimal("4.0"), "메모", "훅\n\n★4.0",
				"#막걸리", keepPhotos());
	}

	private Drink drinkOf(Brewery brewery) {
		Drink other = Drink.of("화요25", "화요25", brewery);
		ReflectionTestUtils.setField(other, "id", 21L);
		when(drinkRepository.findById(21L)).thenReturn(Optional.of(other));
		return other;
	}

	private PostUpdateRequest ratingChange(BigDecimal rating, String head) {
		return new PostUpdateRequest(VERSION, null, null, null, null, rating, "메모", head, "#막걸리", keepPhotos());
	}

	@Test
	void 술을_바꾸고_이전_양조장_이름으로_고치면_400이고_새_양조장은_그대로다() {
		// given
		drinkOf(hwayo);
		BreweryRequest edit = new BreweryRequest("국순당", null, "02-1", null, null, null);

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, drinkChange(21L, edit)));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
		assertThat(hwayo.getName()).isEqualTo("화요");
		assertThat(hwayo.getPhone()).isNull();
		assertThat(hwayo.getSource()).isEqualTo(BrewerySource.PUBLIC_DATA);
		verify(postRepository, never()).transition(any(), any(), any(), anyInt(), any());
	}

	@Test
	void 술을_바꾸고_그_술의_양조장_이름으로_고치면_그_양조장에_반영된다() {
		// given
		drinkOf(hwayo);
		when(captionService.compose(any())).thenReturn(new ComposedCaption("새 캡션", ""));
		BreweryRequest edit = new BreweryRequest("화요", null, "02-1", null, null, null);

		// when
		service.update(POST_ID, drinkChange(21L, edit));

		// then
		assertThat(hwayo.getPhone()).isEqualTo("02-1");
		assertThat(kooksoondang.getPhone()).isNull();
	}

	@Test
	void 양조장이_없는_술로_바꾸고_양조장을_고치면_400이다() {
		// given
		drinkOf(null);
		BreweryRequest edit = new BreweryRequest("국순당", null, "02-1", null, null, null);

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, drinkChange(21L, edit)));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
		assertThat(kooksoondang.getPhone()).isNull();
	}

	@Test
	void 별점만_바꾸면_캡션_별점도_바뀐다() {
		// when
		service.update(POST_ID, ratingChange(new BigDecimal("4.5"), "훅\n\n★4.0"));

		// then
		verifyNoInteractions(captionService);
		assertThat(post.getCaption()).isEqualTo(new CaptionParts("훅\n\n★4.5", "#막걸리").join(kooksoondang));
		assertThat(post.isCaptionEdited()).isFalse();
	}

	@Test
	void 별점을_비우면_캡션_별점_블록을_뺀다() {
		// when
		service.update(POST_ID, ratingChange(null, "훅\n\n★4.0"));

		// then
		assertThat(post.getCaption()).isEqualTo(new CaptionParts("훅", "#막걸리").join(kooksoondang));
	}

	@Test
	void 캡션을_고치면서_별점을_바꾸면_고친_문장을_그대로_둔다() {
		// when
		service.update(POST_ID, ratingChange(new BigDecimal("4.5"), "내가 고친 훅\n\n★4.0"));

		// then
		assertThat(post.getCaption()).isEqualTo(new CaptionParts("내가 고친 훅\n\n★4.0", "#막걸리").join(kooksoondang));
		assertThat(post.isCaptionEdited()).isTrue();
	}

	/** 분석 단계에서 실패해 캡션이 없는 게시물. */
	private Post uncaptionedFailedPost() {
		Post p = Post.receive(PostSource.SHARE, "메모", new BigDecimal("4.0"), CLOCK);
		ReflectionTestUtils.setField(p, "id", POST_ID);
		ReflectionTestUtils.setField(p, "status", PostStatus.FAILED);
		ReflectionTestUtils.setField(p, "version", VERSION);
		p.assignDrink(drink);
		p.recordFailure(PostStatus.ANALYZING, "캡션 생성 실패");
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(p));
		return p;
	}

	private PostUpdateRequest captionless(String head) {
		return new PostUpdateRequest(VERSION, null, null, null, null, new BigDecimal("4.0"), "메모", head, "",
				keepPhotos());
	}

	@Test
	void 캡션이_없는_실패_게시물은_저장할_때_캡션을_만든다() {
		// given
		post = uncaptionedFailedPost();
		String composed = "훅\n\n★4.0\n\n" + BreweryBlock.render(kooksoondang) + "\n\n#막걸리";
		when(captionService.compose(any())).thenReturn(new ComposedCaption(composed, "#막걸리"));

		// when
		service.update(POST_ID, captionless(""));

		// then
		verify(captionService).compose(any());
		assertThat(post.getCaption()).isEqualTo(composed);
		assertThat(post.isCaptionEdited()).isFalse();
	}

	@Test
	void 캡션이_없고_재생성도_실패하면_400이다() {
		// given
		post = uncaptionedFailedPost();
		when(captionService.compose(any())).thenThrow(new ClaudeUnavailableException("down"));

		// when
		ErrorCode code = errorOf(() -> service.update(POST_ID, captionless("")));

		// then
		assertThat(code).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
		assertThat(post.getCaption()).isNull();
		verify(postRepository, never()).transition(any(), any(), any(), anyInt(), any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void 캡션이_없는_게시물에_머리를_직접_쓰면_그_문장을_쓴다() {
		// given
		post = uncaptionedFailedPost();

		// when
		service.update(POST_ID, captionless("내가 쓴 훅"));

		// then
		verifyNoInteractions(captionService);
		assertThat(post.getCaption()).isEqualTo(new CaptionParts("내가 쓴 훅", "").join(kooksoondang));
		assertThat(post.isCaptionEdited()).isTrue();
	}
}
