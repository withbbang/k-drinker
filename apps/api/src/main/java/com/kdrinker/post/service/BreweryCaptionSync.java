package com.kdrinker.post.service;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.event.BreweryUpdatedEvent;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.caption.CaptionParts;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostChannel;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.event.PostHandedOverEvent;
import com.kdrinker.post.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 양조장이 수정되면 대기 중인 게시물의 캡션 양조장 블록을 새 값으로 바꾼다. 양조장 수정 트랜잭션 안에서 동기로 돈다.
 * 텔레그램 담당 게시물은 웹 담당으로 넘기고 {@link PostHandedOverEvent} 를 발행한다.
 */
@Component
@RequiredArgsConstructor
public class BreweryCaptionSync {

	private final PostRepository postRepository;
	private final BreweryRepository breweryRepository;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	@EventListener
	public void onBreweryUpdated(BreweryUpdatedEvent event) {
		Brewery brewery = breweryRepository.findById(event.breweryId()).orElse(null);
		if (brewery == null) {
			return;
		}
		for (Long id : postRepository.findEditableIdsByBreweryId(event.breweryId())) {
			Post post = postRepository.findById(id).orElse(null);
			if (post == null) {
				continue;
			}
			if (CaptionParts.hasBreweryBlock(post.getCaption())) {
				String caption = CaptionParts.split(post.getCaption()).join(brewery);
				post.replaceCaption(caption, post.getHashtags(), post.isCaptionEdited());
			}
			boolean handedOver = post.getChannel() == PostChannel.TELEGRAM;
			if (handedOver) {
				post.moveToWeb();
			}
			PostStatus status = post.getStatus();
			// transition 이 변경을 flush 한 뒤 영속성 컨텍스트를 비운다. 이 뒤로 post 는 쓰지 않는다.
			postRepository.transition(id, status, status, post.getVersion(), Instant.now(clock));
			if (handedOver) {
				events.publishEvent(new PostHandedOverEvent(id));
			}
		}
	}
}
