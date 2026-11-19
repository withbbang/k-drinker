package com.kdrinker.post.service;

import com.kdrinker.analysis.AnalysisService;
import com.kdrinker.brewery.dto.BreweryResponse;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.caption.BreweryBlock;
import com.kdrinker.caption.CaptionAssembler;
import com.kdrinker.caption.CaptionParts;
import com.kdrinker.caption.CaptionValues;
import com.kdrinker.caption.Placeholders;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.common.PageResponse;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.media.service.PhotoService;
import com.kdrinker.post.dto.PostDetailResponse;
import com.kdrinker.post.dto.PostPhotoResponse;
import com.kdrinker.post.dto.PostSummaryResponse;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.publish.PublishService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryService {

	private final PostRepository postRepository;
	private final PostPhotoRepository postPhotoRepository;
	private final PhotoService photoService;

	public PageResponse<PostSummaryResponse> list(int page, int size) {
		return PageResponse.from(postRepository.findAllByOrderByIdDesc(PageRequest.of(page, size))
				.map(this::toSummary));
	}

	public PostDetailResponse detail(Long id) {
		Post post = postRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		Drink drink = post.getDrink();
		Brewery brewery = drink == null ? null : drink.getBrewery();
		CaptionValues values = CaptionValues.of(drink, post.getRating());
		CaptionParts parts = CaptionParts.split(post.getCaption());
		List<PostPhotoResponse> photos = postPhotoRepository.findByPostIdOrderByPositionAsc(id).stream()
				.map(p -> new PostPhotoResponse(p.getId(), p.getPosition(), photoService.publicUrl(p),
						p.getFitMode() == null ? null : p.getFitMode().name()))
				.toList();
		return new PostDetailResponse(post.getId(), post.getStatus().name(), post.getVersion(), editable(post),
				post.getFailedStage() == null ? null : post.getFailedStage().name(), post.getFailureReason(),
				drink == null ? null : drink.getId(), drink == null ? null : drink.getName(),
				brewery == null ? null : BreweryResponse.from(brewery), post.getRating(), post.getMemo(),
				parts.head(), displayBlock(post, brewery, values), parts.tail(), post.isCaptionEdited(),
				post.getCaption() == null ? 0 : CaptionAssembler.length(post.getCaption(), values), photos,
				post.getPermalink(), post.getChannel().name(),
				post.getCaption() == null ? "" : CaptionAssembler.render(post.getCaption(), values), retryable(post),
				Placeholders.isTemplate(post.getCaption()));
	}

	/** 서버가 재시도를 받아 주는 실패인지. 실제 재시도({@link AnalysisService#startRetry}·{@link PublishService#retry})와 같은 규칙. */
	public static boolean retryable(Post post) {
		return AnalysisService.canRetry(post) || PublishService.canRetry(post);
	}

	/** 검토 대기이거나, 인스타그램 컨테이너가 아직 없는 실패일 때만 고칠 수 있다. */
	public static boolean editable(Post post) {
		return post.getStatus() == PostStatus.PENDING_REVIEW
				|| (post.getStatus() == PostStatus.FAILED && post.getIgContainerId() == null);
	}

	/** 고칠 수 없는 글(게시 중·게시됨 등)은 저장된 블록을 보여 준다. 고칠 수 있는 글만 현재 양조장으로 그린다. */
	private static String displayBlock(Post post, Brewery brewery, CaptionValues values) {
		String stored = post.getCaption();
		String body = CaptionParts.breweryBody(stored);
		if (body != null && (Placeholders.isTemplate(stored) || !editable(post))) {
			return Placeholders.fill(body, values).strip();
		}
		return BreweryBlock.render(brewery).replace(BreweryBlock.MARKER + "\n", "")
				.replace("\n" + BreweryBlock.MARKER, "").replace(BreweryBlock.MARKER, "");
	}

	private PostSummaryResponse toSummary(Post post) {
		String thumbnail = postPhotoRepository.findByPostIdOrderByPositionAsc(post.getId()).stream()
				.filter(p -> p.getPosition() == 0).findFirst().map(photoService::publicUrl).orElse(null);
		Drink drink = post.getDrink();
		return new PostSummaryResponse(post.getId(), post.getStatus().name(), drink == null ? null : drink.getName(),
				post.getRating(), thumbnail, post.getPermalink(), post.getCreatedAt(),
				post.getChannel().name());
	}
}
