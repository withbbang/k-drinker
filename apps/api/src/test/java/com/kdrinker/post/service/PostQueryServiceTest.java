package com.kdrinker.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.caption.BreweryBlock;
import com.kdrinker.drink.entity.Drink;
import com.kdrinker.media.service.PhotoService;
import com.kdrinker.post.dto.PostDetailResponse;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostPhotoRepository;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class PostQueryServiceTest {

	private final PostRepository postRepository = mock(PostRepository.class);
	private final PostPhotoRepository postPhotoRepository = mock(PostPhotoRepository.class);
	private final PhotoService photoService = mock(PhotoService.class);
	private final PostQueryService service = new PostQueryService(postRepository, postPhotoRepository, photoService);

	private Post post(PostStatus status) {
		Post post = Post.receive(PostSource.SHARE, "메모", null, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
		ReflectionTestUtils.setField(post, "id", 1L);
		ReflectionTestUtils.setField(post, "status", status);
		return post;
	}

	@Test
	void 상세는_양조장_블록을_마커_없이_준다() {
		// given
		Brewery brewery = Brewery.of("국순당", "국순당", BrewerySource.PUBLIC_DATA);
		Post post = post(PostStatus.PENDING_REVIEW);
		post.assignDrink(Drink.of("막걸리", "막걸리", brewery));
		post.writeCaption("훅\n\n" + BreweryBlock.render(brewery) + "\n\n#태그", "#태그", null, null);
		when(postRepository.findById(1L)).thenReturn(Optional.of(post));
		when(postPhotoRepository.findByPostIdOrderByPositionAsc(1L)).thenReturn(List.of());

		// when
		PostDetailResponse detail = service.detail(1L);

		// then
		assertThat(detail.breweryBlock()).isEqualTo("🍶 국순당").doesNotContain(BreweryBlock.MARKER);
		assertThat(detail.captionHead()).isEqualTo("훅");
		assertThat(detail.captionTail()).isEqualTo("#태그");
		assertThat(detail.brewery().name()).isEqualTo("국순당");
		assertThat(detail.captionLength()).isPositive();
		assertThat(detail.editable()).isTrue();
	}

	@Test
	void 게시용_캡션은_저장된_캡션을_따른다() {
		// given — 캡션을 쓴 뒤 양조장 연락처가 바뀌었다
		Brewery brewery = Brewery.of("국순당", "국순당", BrewerySource.PUBLIC_DATA);
		Post post = post(PostStatus.PENDING_REVIEW);
		post.assignDrink(Drink.of("막걸리", "막걸리", brewery));
		post.writeCaption("훅\n\n" + BreweryBlock.render(brewery) + "\n\n#태그", "#태그", null, null);
		brewery.updateContact("서울시", "02-000-0000", null, null, null, null);
		when(postRepository.findById(1L)).thenReturn(Optional.of(post));
		when(postPhotoRepository.findByPostIdOrderByPositionAsc(1L)).thenReturn(List.of());

		// when
		PostDetailResponse detail = service.detail(1L);

		// then
		assertThat(detail.publishCaption()).isEqualTo("훅\n\n🍶 국순당\n\n#태그")
				.doesNotContain(BreweryBlock.MARKER).doesNotContain("02-000-0000");
		assertThat(detail.breweryBlock()).contains("02-000-0000");
	}

	@Test
	void 캡션이_없으면_게시용_캡션은_빈_문자열이다() {
		// given
		Post post = post(PostStatus.RECEIVED);
		when(postRepository.findById(1L)).thenReturn(Optional.of(post));
		when(postPhotoRepository.findByPostIdOrderByPositionAsc(1L)).thenReturn(List.of());

		// when / then
		assertThat(service.detail(1L).publishCaption()).isEmpty();
	}

	@Test
	void 컨테이너가_있는_실패는_편집_불가다() {
		// given
		Post withContainer = post(PostStatus.FAILED);
		withContainer.recordContainer("c1");
		Post withoutContainer = post(PostStatus.FAILED);

		// when / then
		assertThat(PostQueryService.editable(withContainer)).isFalse();
		assertThat(PostQueryService.editable(withoutContainer)).isTrue();
		assertThat(PostQueryService.editable(post(PostStatus.PUBLISHED))).isFalse();
	}

	@Test
	void 사진이_없으면_썸네일은_null이다() {
		// given
		Post post = post(PostStatus.RECEIVED);
		when(postRepository.findAllByOrderByIdDesc(PageRequest.of(0, 20)))
				.thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 20), 1));
		when(postPhotoRepository.findByPostIdOrderByPositionAsc(1L)).thenReturn(List.of());

		// when
		var page = service.list(0, 20);

		// then
		assertThat(page.items()).hasSize(1);
		assertThat(page.items().get(0).thumbnailUrl()).isNull();
		assertThat(page.totalElements()).isEqualTo(1);
	}

	@Test
	void 상세와_목록에_담당_채널을_준다() {
		// given
		Post post = post(PostStatus.PENDING_REVIEW);
		when(postRepository.findById(1L)).thenReturn(Optional.of(post));
		when(postRepository.findAllByOrderByIdDesc(PageRequest.of(0, 20)))
				.thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 20), 1));
		when(postPhotoRepository.findByPostIdOrderByPositionAsc(1L)).thenReturn(List.of());

		// when
		PostDetailResponse detail = service.detail(1L);
		var page = service.list(0, 20);

		// then
		assertThat(detail.channel()).isEqualTo("WEB");
		assertThat(page.items().get(0).channel()).isEqualTo("WEB");
	}

	@Test
	void 텔레그램_담당이면_TELEGRAM을_준다() {
		// given
		Post post = Post.receive(PostSource.TELEGRAM, null, null, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
		ReflectionTestUtils.setField(post, "id", 1L);
		ReflectionTestUtils.setField(post, "status", PostStatus.PENDING_REVIEW);
		when(postRepository.findById(1L)).thenReturn(Optional.of(post));
		when(postPhotoRepository.findByPostIdOrderByPositionAsc(1L)).thenReturn(List.of());

		// when
		PostDetailResponse detail = service.detail(1L);

		// then
		assertThat(detail.channel()).isEqualTo("TELEGRAM");
	}
}
