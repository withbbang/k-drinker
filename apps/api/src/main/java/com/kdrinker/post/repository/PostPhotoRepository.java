package com.kdrinker.post.repository;

import com.kdrinker.post.entity.PostPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostPhotoRepository extends JpaRepository<PostPhoto, Long> {

	List<PostPhoto> findByPostIdOrderByPositionAsc(Long postId);

}
