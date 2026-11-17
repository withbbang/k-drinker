package com.kdrinker.post.service;

import com.kdrinker.analysis.claude.ClaudeUnavailableException;
import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.brewery.service.BreweryService;
import com.kdrinker.caption.CaptionAssembler;
import com.kdrinker.caption.CaptionInput;
import com.kdrinker.caption.CaptionParts;
import com.kdrinker.caption.CaptionService;
import com.kdrinker.caption.ComposedCaption;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.common.Names;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import com.kdrinker.media.FocusBox;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.media.service.PhotoService;
import com.kdrinker.post.dto.PostDetailResponse;
import com.kdrinker.post.dto.PostUpdateRequest;
import com.kdrinker.post.dto.PostUpdateRequest.PhotoEdit;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostEditedEvent;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 웹 수정 화면의 저장. 검토 대기(또는 컨테이너가 없는 실패) 게시물을 고친 뒤 버전 검사 전이로 PENDING_REVIEW에 두고
 * {@link PostEditedEvent}로 새 개요를 보낸다.
 * <p>
 * 클래스·메서드에 {@code @Transactional}을 두지 않는다. 캡션 재생성(Claude 호출)은 트랜잭션 밖에서 하고, DB 쓰기는
 * {@link TransactionTemplate}으로 짧게 한다({@code AnalysisService}와 같은 이유). 텔레그램 [확인]과 동시에 저장되면
 * 버전 검사 전이에서 하나만 이긴다. 빠진 사진의 파일은 커밋 뒤에만 지운다.
 */
@Slf4j
@Service
public class PostEditService {

	private final PostRepository postRepository;
	private final PostPhotoRepository postPhotoRepository;
	private final DrinkRepository drinkRepository;
	private final BreweryRepository breweryRepository;
	private final BreweryService breweryService;
	private final CaptionService captionService;
	private final PhotoService photoService;
	private final PhotoStorage photoStorage;
	private final PostQueryService postQueryService;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	private final TransactionTemplate tx;

	public PostEditService(PostRepository postRepository, PostPhotoRepository postPhotoRepository,
			DrinkRepository drinkRepository, BreweryRepository breweryRepository, BreweryService breweryService,
			CaptionService captionService, PhotoService photoService, PhotoStorage photoStorage,
			PostQueryService postQueryService, ApplicationEventPublisher events, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.postRepository = postRepository;
		this.postPhotoRepository = postPhotoRepository;
		this.drinkRepository = drinkRepository;
		this.breweryRepository = breweryRepository;
		this.breweryService = breweryService;
		this.captionService = captionService;
		this.photoService = photoService;
		this.photoStorage = photoStorage;
		this.postQueryService = postQueryService;
		this.events = events;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
	}

	/**
	 * 고친 값을 저장하고 다시 읽은 상세를 돌려준다.
	 *
	 * @throws IllegalStateException 트랜잭션 안에서 부른 경우(호출 쪽 버그). Claude 호출이 트랜잭션 안에서 일어나게 된다.
	 */
	public PostDetailResponse update(Long id, PostUpdateRequest req) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("update는 트랜잭션 밖에서 불러야 합니다. postId=" + id);
		}
		Snapshot snapshot = tx.execute(status -> snapshot(id, req));
		NewCaption caption = decideCaption(id, req, snapshot);
		tx.executeWithoutResult(status -> {
			save(id, req, snapshot, caption);
			if (postRepository.transition(id, snapshot.status(), PostStatus.PENDING_REVIEW, snapshot.version(),
					clock.instant()) == 0) {
				status.setRollbackOnly();
				throw new BusinessException(ErrorCode.POST_VERSION_CONFLICT);
			}
			events.publishEvent(new PostEditedEvent(id));
		});
		return postQueryService.detail(id);
	}

	/** 1단계: 검증하고 캡션 결정에 필요한 값을 읽어 둔다. 아무것도 고치지 않는다. */
	private Snapshot snapshot(Long id, PostUpdateRequest req) {
		Post post = postRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (!PostQueryService.editable(post)) {
			throw new BusinessException(ErrorCode.POST_NOT_EDITABLE);
		}
		if (post.getVersion() != req.version()) {
			throw new BusinessException(ErrorCode.POST_VERSION_CONFLICT);
		}
		checkPhotos(req.photos(), postPhotoRepository.findByPostIdOrderByPositionAsc(id));
		PostIntakeService.validateMemoAndRating(req.memo(), req.rating());
		if (req.drinkId() != null && req.newDrinkName() != null) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}

		Drink currentDrink = post.getDrink();
		Long currentDrinkId = currentDrink == null ? null : currentDrink.getId();
		Brewery currentBrewery = currentDrink == null ? null : currentDrink.getBrewery();
		Long currentBreweryId = currentBrewery == null ? null : currentBrewery.getId();

		Drink targetDrink;
		String drinkName;
		if (req.drinkId() != null) {
			targetDrink = findDrink(req.drinkId());
			drinkName = targetDrink.getName();
		} else if (req.newDrinkName() != null) {
			targetDrink = null;
			drinkName = newDrinkName(req.newDrinkName());
		} else {
			targetDrink = currentDrink;
			drinkName = currentDrink == null ? null : currentDrink.getName();
		}
		boolean hasDrink = targetDrink != null || req.newDrinkName() != null;

		Brewery targetBrewery;
		if (req.breweryId() != null) {
			if (!hasDrink) {
				// 양조장은 술에 붙는다. 술이 없는 게시물에는 양조장만 고를 수 없다.
				throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
			}
			targetBrewery = findBrewery(req.breweryId());
		} else if (targetDrink != null) {
			targetBrewery = targetDrink.getBrewery();
		} else {
			targetBrewery = currentBrewery;
		}
		Long targetBreweryId = targetBrewery == null ? null : targetBrewery.getId();
		if (req.breweryEdit() != null && targetBrewery == null) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		if (req.breweryId() == null && req.breweryEdit() != null
				&& !targetBrewery.getName().strip().equals(req.breweryEdit().name().strip())) {
			// 술을 바꾸면 양조장도 그 술의 양조장이 된다. 이전 양조장 이름으로 고친 값을 다른 양조장에 덮어쓰지 않는다.
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}

		// 캡션 블록에 쓸 저장하지 않는 사본. breweryEdit를 미리 적용해 검증도 여기서 끝낸다(4단계가 같은 값을 레코드에 넣는다).
		Brewery captionBrewery = targetBrewery == null ? null : copyOf(targetBrewery);
		if (req.breweryEdit() != null) {
			breweryService.applyEdit(captionBrewery, req.breweryEdit());
		}
		LabelResult label = new LabelResult(drinkName, targetDrink == null ? null : targetDrink.getCategory(),
				targetDrink == null ? null : targetDrink.getAbv(), targetDrink == null ? null : targetDrink.getVolumeMl(),
				captionBrewery == null ? null : captionBrewery.getName(), req.rating(), post.getTasteNotes(),
				post.getPairing(), List.of());
		boolean identityChanged = req.newDrinkName() != null
				|| !Objects.equals(targetDrink == null ? null : targetDrink.getId(), currentDrinkId)
				|| !Objects.equals(targetBreweryId, currentBreweryId) || req.breweryEdit() != null;
		return new Snapshot(post.getStatus(), post.getVersion(), CaptionParts.split(post.getCaption()),
				post.isCaptionEdited(), identityChanged, label, captionBrewery, targetBreweryId, post.getRating(),
				post.getCaption() == null || post.getCaption().isBlank());
	}

	/** 사진이 한 장 이상이고, 모두 이 게시물의 사진이며 겹치지 않아야 한다. */
	private static void checkPhotos(List<PhotoEdit> edits, List<PostPhoto> existing) {
		if (edits.isEmpty()) {
			throw new BusinessException(ErrorCode.POST_PHOTO_COUNT);
		}
		Set<Long> ids = existing.stream().map(PostPhoto::getId).collect(Collectors.toSet());
		Set<Long> seen = new HashSet<>();
		for (PhotoEdit edit : edits) {
			if (!ids.contains(edit.id()) || !seen.add(edit.id())) {
				throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
			}
		}
	}

	/**
	 * 2·3단계: 직접 고친 캡션이거나 술·양조장이 그대로면 양조장 블록만 바꾼다. 아니면 트랜잭션 밖에서 다시 만든다.
	 * 이번 요청에서 캡션 문장을 고치지 않았으면 머리 끝의 별점 블록도 새 별점으로 맞춘다.
	 * 캡션이 없는 게시물(분석 단계 실패)은 사용자가 문장을 쓰지 않았으면 새로 만들고, 만들지 못하면 저장하지 않는다.
	 */
	private NewCaption decideCaption(Long id, PostUpdateRequest req, Snapshot snapshot) {
		CaptionParts edited = new CaptionParts(req.captionHead().strip(), req.captionTail().strip());
		boolean editedNow = !edited.equals(snapshot.parts());
		boolean textEdited = snapshot.captionEdited() || editedNow;
		boolean generate = snapshot.captionMissing() ? !editedNow : snapshot.identityChanged() && !textEdited;
		if (generate) {
			try {
				ComposedCaption composed = captionService.compose(new CaptionInput(snapshot.label(), req.rating(),
						snapshot.captionBrewery(), req.memo()));
				return new NewCaption(composed.caption(), composed.hashtags(), false);
			} catch (ClaudeUnavailableException e) {
				log.warn("캡션 재생성에 실패해 양조장 블록만 바꿉니다. postId={} cause={}", id, e.toString());
			}
		}
		CaptionParts parts = editedNow ? edited : edited.withRating(snapshot.rating(), req.rating());
		if (snapshot.captionMissing() && edited.head().isEmpty()) {
			// 본문 없는 캡션으로 검토 대기에 두면 [확인] 한 번에 빈 캡션이 게시된다.
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		return new NewCaption(parts.join(snapshot.captionBrewery()), parts.tail(), textEdited);
	}

	/** 4단계: 전이 전에 고칠 필드를 모두 고친다. 예외가 나면 템플릿이 롤백하고 파일은 그대로다. */
	private void save(Long id, PostUpdateRequest req, Snapshot snapshot, NewCaption caption) {
		Post post = postRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (post.getStatus() != snapshot.status() || post.getVersion() != snapshot.version()) {
			throw new BusinessException(ErrorCode.POST_VERSION_CONFLICT);
		}
		Drink drink = resolveDrink(post, req, snapshot);
		if (req.breweryId() != null && drink != null) {
			drink.changeBrewery(findBrewery(req.breweryId()));
		}
		if (req.breweryEdit() != null) {
			Brewery brewery = drink == null ? null : drink.getBrewery();
			if (brewery == null) {
				throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
			}
			breweryService.applyEdit(brewery, req.breweryEdit());
		}
		post.assignDrink(drink);
		post.recordRating(req.rating());
		post.updateMemo(req.memo());
		if (CaptionAssembler.length(caption.caption()) > CaptionService.MAX_LENGTH) {
			throw new BusinessException(ErrorCode.POST_CAPTION_TOO_LONG);
		}
		post.replaceCaption(caption.caption(), caption.hashtags(), caption.edited());
		applyPhotos(id, req.photos());
		post.clearFailure();
	}

	private Drink resolveDrink(Post post, PostUpdateRequest req, Snapshot snapshot) {
		if (req.drinkId() != null) {
			return findDrink(req.drinkId());
		}
		if (req.newDrinkName() != null) {
			String name = newDrinkName(req.newDrinkName());
			Brewery ref = snapshot.targetBreweryId() == null ? null
					: breweryRepository.getReferenceById(snapshot.targetBreweryId());
			return drinkRepository.save(Drink.of(name, Names.normalize(name), ref));
		}
		return post.getDrink();
	}

	/** 빠진 사진은 행을 지우고 커밋 뒤 파일을 지운다. 남은 사진은 순서를 다시 매기고, 맞춤 방식이 바뀐 것만 다시 렌더링한다. */
	private void applyPhotos(Long postId, List<PhotoEdit> edits) {
		List<PostPhoto> existing = postPhotoRepository.findByPostIdOrderByPositionAsc(postId);
		checkPhotos(edits, existing);
		Map<Long, PostPhoto> byId = existing.stream().collect(Collectors.toMap(PostPhoto::getId, Function.identity()));
		Set<Long> kept = edits.stream().map(PhotoEdit::id).collect(Collectors.toSet());
		List<String> doomed = new ArrayList<>();
		for (PostPhoto photo : existing) {
			if (!kept.contains(photo.getId())) {
				doomed.add(photo.getOriginalPath());
				doomed.add(photo.getRenderedPath());
				postPhotoRepository.delete(photo);
			}
		}
		deleteAfterCommit(postId, doomed);
		for (int i = 0; i < edits.size(); i++) {
			PhotoEdit edit = edits.get(i);
			PostPhoto photo = byId.get(edit.id());
			photo.moveTo(i);
			if (photo.getFitMode() != edit.fitMode()) {
				photoService.rerender(photo.getId(), focusOf(photo), edit.fitMode());
			}
		}
	}

	private void deleteAfterCommit(Long postId, List<String> paths) {
		if (paths.isEmpty()) {
			return;
		}
		// 커밋 전에는 공개 파일을 건드리지 않는다. 롤백되면 이 콜백은 불리지 않는다.
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (String path : paths) {
					if (path != null) {
						photoStorage.deleteQuietly(path);
					}
				}
				log.info("수정 저장으로 뺀 사진 파일을 지웠습니다. postId={} files={}", postId, paths.size());
			}
		});
	}

	private static FocusBox focusOf(PostPhoto photo) {
		if (photo.getFocusX() == null || photo.getFocusY() == null || photo.getFocusW() == null
				|| photo.getFocusH() == null) {
			return null;
		}
		return new FocusBox(photo.getFocusX(), photo.getFocusY(), photo.getFocusW(), photo.getFocusH());
	}

	private Drink findDrink(Long id) {
		return drinkRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED));
	}

	private Brewery findBrewery(Long id) {
		return breweryRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED));
	}

	/** 새 술 이름. 정규화하면 비는 이름은 받지 않는다. */
	private static String newDrinkName(String raw) {
		if (Names.normalize(raw).isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		return raw.strip();
	}

	/** 트랜잭션 밖에서 읽어도 되는(지연 로딩이 없는) 양조장 사본. 캡션 블록에 쓰는 칸만 옮긴다. */
	private static Brewery copyOf(Brewery b) {
		Brewery copy = Brewery.of(b.getName(), b.getNormalizedName(), BrewerySource.MANUAL);
		copy.updateContact(b.getAddress(), b.getPhone(), b.getHomepage(), b.getInstagramHandle(), b.getOtherSns(),
				null);
		return copy;
	}

	private record Snapshot(PostStatus status, int version, CaptionParts parts, boolean captionEdited,
			boolean identityChanged, LabelResult label, Brewery captionBrewery, Long targetBreweryId,
			BigDecimal rating, boolean captionMissing) {
	}

	private record NewCaption(String caption, String hashtags, boolean edited) {
	}
}
