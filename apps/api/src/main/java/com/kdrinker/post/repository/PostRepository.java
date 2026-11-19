package com.kdrinker.post.repository;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

	/** 상태가 from 일 때만 to 로 바꾸고 version 을 올린다. 호출 전 PostStatus.canTransitionTo 를 확인하고, 전이 후엔 엔티티를 다시 읽는다(영속성 컨텍스트가 비워짐). 텔레그램 콜백은 버전 검사 메서드를 쓴다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Post p
			   set p.status = :to, p.version = p.version + 1, p.updatedAt = :now
			 where p.id = :id and p.status = :from
			""")
	int transition(@Param("id") Long id, @Param("from") PostStatus from, @Param("to") PostStatus to,
			@Param("now") Instant now);

	/** 상태·version 이 모두 맞을 때만 전이한다. 텔레그램 콜백은 이 메서드를 쓴다. 호출 전 PostStatus.canTransitionTo 를 확인하고, 전이 후엔 엔티티를 다시 읽는다(영속성 컨텍스트가 비워짐). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Post p
			   set p.status = :to, p.version = p.version + 1, p.updatedAt = :now
			 where p.id = :id and p.status = :from and p.version = :expectedVersion
			""")
	int transition(@Param("id") Long id, @Param("from") PostStatus from, @Param("to") PostStatus to,
			@Param("expectedVersion") int expectedVersion, @Param("now") Instant now);

	@Query("select p.id from Post p where p.status = :status order by p.id")
	List<Long> findIdsByStatus(@Param("status") PostStatus status);

	/** 상태가 같고 since 이후에 갱신된 게시물 id. 기동 알림 보정이 쓴다. */
	@Query("select p.id from Post p where p.status = :status and p.updatedAt >= :since order by p.id")
	List<Long> findIdsByStatusUpdatedSince(@Param("status") PostStatus status, @Param("since") Instant since);

	/** 상태가 같고 before 보다 먼저 갱신된(경계 제외) 게시물 id. 취소 사진 정리가 쓴다. */
	@Query("select p.id from Post p where p.status = :status and p.updatedAt < :before order by p.id")
	List<Long> findIdsByStatusUpdatedBefore(@Param("status") PostStatus status, @Param("before") Instant before);

	/** 양조장 수정이 캡션에 반영돼야 하는 게시물 id. 캡션이 있고 검토 대기이거나, 컨테이너 없이 실패한 것. */
	@Query("""
			select p.id from Post p
			 where p.drink.brewery.id = :breweryId and p.caption is not null
			   and (p.status = com.kdrinker.post.entity.PostStatus.PENDING_REVIEW
			        or (p.status = com.kdrinker.post.entity.PostStatus.FAILED and p.igContainerId is null))
			 order by p.id
			""")
	List<Long> findEditableIdsByBreweryId(@Param("breweryId") Long breweryId);

	/** 술의 양조장이 바뀌면 캡션에 반영돼야 하는 게시물 id. 조건은 {@link #findEditableIdsByBreweryId}와 같다. */
	@Query("""
			select p.id from Post p
			 where p.drink.id = :drinkId and p.caption is not null
			   and (p.status = com.kdrinker.post.entity.PostStatus.PENDING_REVIEW
			        or (p.status = com.kdrinker.post.entity.PostStatus.FAILED and p.igContainerId is null))
			 order by p.id
			""")
	List<Long> findEditableIdsByDrinkId(@Param("drinkId") Long drinkId);

	@EntityGraph(attributePaths = { "drink", "drink.brewery" })
	Page<Post> findAllByOrderByIdDesc(Pageable pageable);

}
