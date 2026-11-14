package com.kdrinker.instagram.repository;

import com.kdrinker.instagram.entity.InstagramToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstagramTokenRepository extends JpaRepository<InstagramToken, Integer> {
}
