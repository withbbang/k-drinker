package com.kdrinker.post.service;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.event.BreweryUpdatedEvent;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.caption.CaptionParts;
import com.kdrinker.caption.Placeholders;
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
 * 양조장이 수정되면 대기 중인 게시물의 캡션 양조장 블록을 새 값으로 바꾼다(이름표 캡션은 문자열을 두고 버전·담당만 처리). 양조장 수정 트랜잭션 안에서 동기로 돈다.
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
			if (post == null || !CaptionParts.hasBreweryBlock(post.getCaption())) {
				continue;
			}
			// 이름표 캡션은 문자열을 그대로 둔다. 채운 글이 양조장 값에 따라 바뀌므로 버전·담당 처리는 한다.
			boolean template = Placeholders.isTemplate(post.getCaption());
			String caption = template ? post.getCaption() : CaptionParts.split(post.getCaption()).join(brewery);
			if (!template && caption.equals(post.getCaption())) {
				continue;
			}
			PostStatus status = post.getStatus();
			// 상태·version 이 맞을 때만 먼저 선점한다. 동시에 상태가 바뀐 게시물은 건드리지 않는다.
			// transition 이 flush 한 뒤 영속성 컨텍스트를 비우므로 이 뒤로 post 는 다시 읽는다.
			if (postRepository.transition(id, status, status, post.getVersion(), Instant.now(clock)) == 0) {
				continue;
			}
			Post fresh = postRepository.findById(id).orElse(null);
			if (fresh == null || !PostQueryService.editable(fresh)) {
				continue;
			}
			fresh.replaceCaption(caption, fresh.getHashtags(), fresh.isCaptionEdited());
			boolean handedOver = fresh.getChannel() == PostChannel.TELEGRAM;
			if (handedOver) {
				fresh.moveToWeb();
			}
			postRepository.saveAndFlush(fresh);
			if (handedOver) {
				events.publishEvent(new PostHandedOverEvent(id));
			}
		}
	}
}
