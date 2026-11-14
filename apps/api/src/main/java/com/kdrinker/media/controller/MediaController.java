package com.kdrinker.media.controller;

import com.kdrinker.media.service.PhotoService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인스타그램이 가져가는 공개 미디어. 렌더링된 JPEG만 내려주고 원본은 서빙하지 않는다. */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

	private final PhotoService photoService;

	@GetMapping("/{token}.jpg")
	public ResponseEntity<byte[]> get(@PathVariable("token") String token) {
		return photoService.readRendered(token)
				.map(bytes -> ResponseEntity.ok()
						.contentType(MediaType.IMAGE_JPEG)
						.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
						.body(bytes))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
