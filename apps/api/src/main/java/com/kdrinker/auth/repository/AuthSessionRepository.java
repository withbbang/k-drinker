package com.kdrinker.auth.repository;

import com.kdrinker.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

}
