package com.kdrinker.telegram.repository;

import com.kdrinker.telegram.entity.NotificationKind;
import com.kdrinker.telegram.entity.TelegramNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramNotificationRepository extends JpaRepository<TelegramNotification, Long> {

	boolean existsByDedupKey(String dedupKey);

	List<TelegramNotification> findByPostIdAndKindAndMessageIdIsNotNullOrderByIdAsc(Long postId, NotificationKind kind);

}
