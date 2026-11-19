package com.kdrinker.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.post.entity.FitMode;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostChannel;
import com.kdrinker.post.entity.PostPhoto;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.drink.entity.Drink;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class PostRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-11-14T03:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostPhotoRepository postPhotoRepository;

	@Autowired
	private EntityManager em;

	@Test
	void 상태가_맞을_때만_전이하고_버전을_올린다() {
		// given
		Post post = Post.receive(PostSource.TELEGRAM, "향이 좋다", new BigDecimal("4.5"), CLOCK);
		ReflectionTestUtils.setField(post, "status", PostStatus.PENDING_REVIEW);
		Long id = postRepository.saveAndFlush(post).getId();
		Instant later = NOW.plusSeconds(60);

		// when
		int first = postRepository.transition(id, PostStatus.PENDING_REVIEW, PostStatus.PUBLISHING, later);
		int second = postRepository.transition(id, PostStatus.PENDING_REVIEW, PostStatus.PUBLISHING, later);

		// then
		Post reloaded = postRepository.findById(id).orElseThrow();
		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		assertThat(reloaded.getStatus()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(reloaded.getVersion()).isEqualTo(1);
		assertThat(reloaded.getUpdatedAt()).isEqualTo(later);
	}

	@Test
	void 버전이_다르면_전이하지_않고_맞으면_전이하며_버전을_올린다() {
		// given
		Post post = Post.receive(PostSource.TELEGRAM, "향이 좋다", null, CLOCK);
		ReflectionTestUtils.setField(post, "status", PostStatus.PENDING_REVIEW);
		Long id = postRepository.saveAndFlush(post).getId();
		Instant later = NOW.plusSeconds(60);

		// when
		int mismatch = postRepository.transition(id, PostStatus.PENDING_REVIEW, PostStatus.PUBLISHING, 5, later);
		Post unchanged = postRepository.findById(id).orElseThrow();
		PostStatus statusAfterMismatch = unchanged.getStatus();
		long versionAfterMismatch = unchanged.getVersion();
		int match = postRepository.transition(id, PostStatus.PENDING_REVIEW, PostStatus.PUBLISHING, 0, later);

		// then
		Post reloaded = postRepository.findById(id).orElseThrow();
		assertThat(mismatch).isZero();
		assertThat(statusAfterMismatch).isEqualTo(PostStatus.PENDING_REVIEW);
		assertThat(versionAfterMismatch).isZero();
		assertThat(match).isEqualTo(1);
		assertThat(reloaded.getStatus()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(reloaded.getVersion()).isEqualTo(1);
		assertThat(reloaded.getUpdatedAt()).isEqualTo(later);
	}

	@Test
	void 오래된_인스턴스를_저장해도_전이된_상태와_버전을_덮어쓰지_않는다() {
		// given
		Post post = Post.receive(PostSource.TELEGRAM, "향이 좋다", null, CLOCK);
		ReflectionTestUtils.setField(post, "status", PostStatus.PENDING_REVIEW);
		Long id = postRepository.saveAndFlush(post).getId();
		Post stale = postRepository.findById(id).orElseThrow();
		postRepository.transition(id, PostStatus.PENDING_REVIEW, PostStatus.PUBLISHING, NOW.plusSeconds(60));

		// when
		stale.editCaption("수정한 캡션");
		postRepository.saveAndFlush(stale);
		em.clear();

		// then
		Post reloaded = postRepository.findById(id).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(PostStatus.PUBLISHING);
		assertThat(reloaded.getVersion()).isEqualTo(1);
		assertThat(reloaded.getCaption()).isEqualTo("수정한 캡션");
	}

	@Test
	void 담당을_적지_않고_넣은_행은_텔레그램_담당이다() {
		// given: V10 이전에 있던 행처럼 channel 없이 넣는다
		em.createNativeQuery("INSERT INTO post (caption_edited, status, version, source, created_at, updated_at) "
				+ "VALUES (FALSE, 'PENDING_REVIEW', 0, 'TELEGRAM', ?1, ?1)")
				.setParameter(1, java.sql.Timestamp.from(NOW))
				.executeUpdate();

		// when
		Object channel = em.createNativeQuery("SELECT channel FROM post").getSingleResult();

		// then
		assertThat(channel).isEqualTo("TELEGRAM");
	}

	@Test
	void 웹으로_넘긴_담당이_저장된다() {
		// given
		Post post = postRepository.saveAndFlush(Post.receive(PostSource.TELEGRAM, null, null, CLOCK));

		// when
		post.moveToWeb();
		postRepository.saveAndFlush(post);
		em.clear();

		// then
		assertThat(postRepository.findById(post.getId()).orElseThrow().getChannel()).isEqualTo(PostChannel.WEB);
	}

	@Test
	void 사진은_position_순서로_조회된다() {
		// given
		Post post = postRepository.saveAndFlush(Post.receive(PostSource.SHARE, null, null, CLOCK));
		postPhotoRepository.save(PostPhoto.of(post, 2, "o2", "token-2", FitMode.CROP));
		postPhotoRepository.save(PostPhoto.of(post, 0, "o0", "token-0", FitMode.PAD));
		postPhotoRepository.save(PostPhoto.of(post, 1, "o1", "token-1", FitMode.CROP));
		postPhotoRepository.flush();
		em.clear();

		// when
		List<PostPhoto> photos = postPhotoRepository.findByPostIdOrderByPositionAsc(post.getId());

		// then
		assertThat(photos).extracting(PostPhoto::getPosition).containsExactly(0, 1, 2);
		assertThat(photos).extracting(PostPhoto::getOriginalPath).containsExactly("o0", "o1", "o2");
	}

	@Test
	void 긴_맛노트_페어링_해시태그도_저장된다() {
		// given
		Post post = Post.receive(PostSource.TELEGRAM, "향이 좋다", null, CLOCK);
		post.writeCaption("캡션", "#".repeat(500), "맛".repeat(1000), "전".repeat(1000));

		// when
		Long id = postRepository.saveAndFlush(post).getId();
		em.clear();

		// then
		Post reloaded = postRepository.findById(id).orElseThrow();
		assertThat(reloaded.getTasteNotes()).hasSize(1000);
		assertThat(reloaded.getPairing()).hasSize(1000);
		assertThat(reloaded.getHashtags()).hasSize(500);
	}

	private Post savePost(Drink drink, PostStatus status, String caption, String containerId) {
		Post post = Post.receive(PostSource.TELEGRAM, "메모", null, CLOCK);
		post.assignDrink(drink);
		if (caption != null) {
			post.writeCaption(caption, "#태그", null, null);
		}
		if (containerId != null) {
			post.recordContainer(containerId);
		}
		ReflectionTestUtils.setField(post, "status", status);
		return postRepository.saveAndFlush(post);
	}

	@Test
	void findEditableIdsByBreweryId_returnsOnlyEditableWithCaption() {
		// given
		Brewery brewery = em.merge(Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA));
		Brewery other = em.merge(Brewery.of("다른", "다른", BrewerySource.PUBLIC_DATA));
		Drink drink = em.merge(Drink.of("화요25", "화요25", brewery));
		Drink otherDrink = em.merge(Drink.of("다른술", "다른술", other));
		Long pending = savePost(drink, PostStatus.PENDING_REVIEW, "캡션", null).getId();
		Long failedNoContainer = savePost(drink, PostStatus.FAILED, "캡션", null).getId();
		savePost(drink, PostStatus.FAILED, "캡션", "container-1");
		savePost(drink, PostStatus.PUBLISHED, "캡션", null);
		savePost(drink, PostStatus.PENDING_REVIEW, null, null);
		savePost(otherDrink, PostStatus.PENDING_REVIEW, "캡션", null);
		em.clear();

		// when
		List<Long> ids = postRepository.findEditableIdsByBreweryId(brewery.getId());

		// then
		assertThat(ids).containsExactly(pending, failedNoContainer);
	}
}
