package com.kdrinker.analysis;

import com.kdrinker.analysis.brewery.BreweryMatcher;
import com.kdrinker.analysis.claude.ClaudeUnavailableException;
import com.kdrinker.analysis.image.ClaudeImages;
import com.kdrinker.analysis.label.LabelReader;
import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.analysis.label.PhotoBox;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.brewery.service.BreweryLookup;
import com.kdrinker.caption.CaptionInput;
import com.kdrinker.caption.CaptionService;
import com.kdrinker.caption.ComposedCaption;
import com.kdrinker.common.Names;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.drink.repository.DrinkRepository;
import com.kdrinker.media.PhotoStorage;
import com.kdrinker.media.service.PhotoService;
import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostAnalyzedEvent;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 접수된 게시물을 분석해 검토 대기(PENDING_REVIEW) 또는 실패(FAILED)로 만든다.
 * <p>
 * 클래스·메서드에 {@code @Transactional}을 두지 않는다. Claude 호출(라벨·양조장 조사·캡션)은 트랜잭션 밖에서 하고,
 * DB 쓰기는 {@link TransactionTemplate}으로 짧게 나눈다. 바깥 트랜잭션이 있으면 BreweryMatcher의 트랜잭션이 합류해
 * 예외 시 rollback-only가 되고, 실패 기록까지 롤백되기 때문이다.
 */
@Slf4j
@Service
public class AnalysisService {

	private final PostRepository postRepository;
	private final PostPhotoRepository postPhotoRepository;
	private final DrinkRepository drinkRepository;
	private final BreweryRepository breweryRepository;
	private final BreweryLookup breweryLookup;
	private final PhotoStorage photoStorage;
	private final PhotoService photoService;
	private final ClaudeImages claudeImages;
	private final LabelReader labelReader;
	private final BreweryMatcher breweryMatcher;
	private final CaptionService captionService;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	private final TransactionTemplate tx;

	public AnalysisService(PostRepository postRepository, PostPhotoRepository postPhotoRepository,
			DrinkRepository drinkRepository, BreweryRepository breweryRepository, BreweryLookup breweryLookup,
			PhotoStorage photoStorage, PhotoService photoService, ClaudeImages claudeImages, LabelReader labelReader,
			BreweryMatcher breweryMatcher, CaptionService captionService, ApplicationEventPublisher events,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.postRepository = postRepository;
		this.postPhotoRepository = postPhotoRepository;
		this.drinkRepository = drinkRepository;
		this.breweryRepository = breweryRepository;
		this.breweryLookup = breweryLookup;
		this.photoStorage = photoStorage;
		this.photoService = photoService;
		this.claudeImages = claudeImages;
		this.labelReader = labelReader;
		this.breweryMatcher = breweryMatcher;
		this.captionService = captionService;
		this.events = events;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
	}

	/**
	 * from(RECEIVED 최초, FAILED 재시도) → ANALYZING → PENDING_REVIEW | FAILED. 상태가 from이 아니면 아무것도 하지 않는다.
	 * 상태를 바꿨으면 끝난 뒤 {@link PostAnalyzedEvent}를 정확히 한 번 발행한다. 예외를 밖으로 던지지 않는다.
	 */
	public void analyze(Long postId, PostStatus from) {
		if (!start(postId, from)) {
			return;
		}
		PostStatus result;
		try {
			run(postId);
			result = PostStatus.PENDING_REVIEW;
		} catch (Exception e) {
			result = fail(postId, e);
		}
		if (result != null) {
			events.publishEvent(new PostAnalyzedEvent(postId, result));
		}
	}

	/**
	 * 짧은 트랜잭션으로 ANALYZING 상태의 게시물을 실패로 바꾼다. 다른 상태면(경합) 아무것도 바꾸지 않고 false.
	 * 기동 복구도 이 메서드를 쓴다.
	 */
	public boolean recordFailure(Long postId, AnalysisFailure reason) {
		return Boolean.TRUE.equals(tx.execute(status -> {
			Post post = postRepository.findById(postId).orElse(null);
			if (post == null || post.getStatus() != PostStatus.ANALYZING) {
				return false;
			}
			post.recordFailure(PostStatus.ANALYZING, reason.message());
			if (postRepository.transition(postId, PostStatus.ANALYZING, PostStatus.FAILED, clock.instant()) == 0) {
				status.setRollbackOnly();
				return false;
			}
			return true;
		}));
	}

	/** 1단계: 실패 정보를 지우고 from → ANALYZING. 갱신 0건이면 롤백(실패 정보도 그대로)하고 false. */
	private boolean start(Long postId, PostStatus from) {
		if (!from.canTransitionTo(PostStatus.ANALYZING)) {
			return false;
		}
		return Boolean.TRUE.equals(tx.execute(status -> {
			Post post = postRepository.findById(postId).orElse(null);
			if (post == null || post.getStatus() != from) {
				return false;
			}
			post.clearFailure();
			if (postRepository.transition(postId, from, PostStatus.ANALYZING, clock.instant()) == 0) {
				status.setRollbackOnly();
				return false;
			}
			return true;
		}));
	}

	/** 2~6단계. 실패하면 예외를 던진다. */
	private void run(Long postId) {
		Snapshot snapshot = tx.execute(status -> snapshot(postId));
		LabelResult label = readLabel(snapshot);
		if (label.drinkName() == null) {
			throw new AnalysisAbort(AnalysisFailure.LABEL_UNREADABLE);
		}
		Brewery brewery = breweryMatcher.match(label).orElse(null);
		BigDecimal rating = snapshot.rating() != null ? snapshot.rating() : label.rating();
		ComposedCaption composed = captionService.compose(new CaptionInput(label, rating, brewery, snapshot.memo()));
		tx.executeWithoutResult(status -> save(postId, snapshot, label, brewery, rating, composed));
	}

	private Snapshot snapshot(Long postId) {
		Post post = postRepository.findById(postId).orElseThrow();
		List<PhotoRef> photos = postPhotoRepository.findByPostIdOrderByPositionAsc(postId).stream()
				.map(p -> new PhotoRef(p.getId(), p.getOriginalPath()))
				.toList();
		return new Snapshot(post.getMemo(), post.getRating(), photos);
	}

	/** 사진을 순서대로 한 장씩 준비해 준비된(축소된) 바이트만 모은다. 원본 바이트는 장마다 버린다. */
	private LabelResult readLabel(Snapshot snapshot) {
		List<byte[]> images = new ArrayList<>(snapshot.photos().size());
		for (PhotoRef photo : snapshot.photos()) {
			images.add(claudeImages.prepare(photoStorage.locate(photo.originalPath())));
		}
		LabelResult raw = labelReader.read(images, snapshot.memo());
		if (raw == null) {
			throw new AnalysisAbort(AnalysisFailure.LABEL_UNREADABLE);
		}
		return raw.sanitized(images.size());
	}

	/** 6단계: 술 연결·별점·캡션·상자 렌더링을 고친 뒤 ANALYZING → PENDING_REVIEW. */
	private void save(Long postId, Snapshot snapshot, LabelResult label, Brewery brewery, BigDecimal rating,
			ComposedCaption composed) {
		Post post = postRepository.findById(postId).orElseThrow();
		Drink drink = breweryLookup.findDrink(label.drinkName()).orElseGet(() -> createDrink(label, brewery));
		post.assignDrink(drink);
		post.recordRating(rating);
		post.writeCaption(composed.caption(), composed.hashtags(), label.tasteNotes(), label.pairing());
		Set<Integer> rendered = new HashSet<>();
		for (PhotoBox box : label.boxes()) {
			if (rendered.add(box.index())) {
				photoService.rerender(snapshot.photos().get(box.index()).id(), box.box(), FitMode.CROP);
			}
		}
		if (postRepository.transition(postId, PostStatus.ANALYZING, PostStatus.PENDING_REVIEW, clock.instant()) == 0) {
			throw new IllegalStateException("분석 중 게시물 상태가 바뀌었습니다. postId=" + postId);
		}
	}

	/** 새로 만드는 술에만 라벨 정보를 넣는다. 이미 있는 술은 별칭 등 손본 정보를 지키려고 그대로 둔다. */
	private Drink createDrink(LabelResult label, Brewery brewery) {
		Brewery ref = brewery == null ? null : breweryRepository.getReferenceById(brewery.getId());
		Drink drink = Drink.of(label.drinkName(), Names.normalize(label.drinkName()), ref);
		drink.updateSpec(label.category(), label.abv(), label.volumeMl(), null);
		return drinkRepository.save(drink);
	}

	/** 7단계: 실패를 기록한다. 기록조차 실패하면 로그만 남기고 null(게시물은 ANALYZING으로 남아 다음 기동 때 복구된다). */
	private PostStatus fail(Long postId, Exception e) {
		AnalysisFailure reason = classify(e);
		if (reason == AnalysisFailure.UNKNOWN) {
			log.error("분석 실패(알 수 없음) postId={}", postId, e);
		} else {
			log.warn("분석 실패 postId={} reason={} cause={}", postId, reason, e.toString());
		}
		try {
			return recordFailure(postId, reason) ? PostStatus.FAILED : null;
		} catch (RuntimeException recordError) {
			log.error("분석 실패를 기록하지 못했습니다. 게시물이 ANALYZING으로 남습니다. postId={}", postId, recordError);
			return null;
		}
	}

	private static AnalysisFailure classify(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof AnalysisAbort abort) {
				return abort.reason;
			}
			if (t instanceof ClaudeUnavailableException) {
				return AnalysisFailure.CLAUDE_UNAVAILABLE;
			}
		}
		return AnalysisFailure.UNKNOWN;
	}

	private record PhotoRef(Long id, String originalPath) {
	}

	private record Snapshot(String memo, BigDecimal rating, List<PhotoRef> photos) {
	}

	/** 사유가 정해진 실패. 스택트레이스가 필요 없다. */
	private static final class AnalysisAbort extends RuntimeException {

		private final transient AnalysisFailure reason;

		AnalysisAbort(AnalysisFailure reason) {
			super(reason.message(), null, false, false);
			this.reason = reason;
		}
	}
}
