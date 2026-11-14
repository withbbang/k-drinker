package com.kdrinker.post.repository;

import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

	/** 상태가 from 일 때만 to 로 바꾸고 version 을 올린다. 바뀐 행 수(0 또는 1)를 돌려준다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Post p
			   set p.status = :to, p.version = p.version + 1, p.updatedAt = :now
			 where p.id = :id and p.status = :from
			""")
	int transition(@Param("id") Long id, @Param("from") PostStatus from, @Param("to") PostStatus to,
			@Param("now") Instant now);

}
