package com.kdrinker.media.controller;

import com.kdrinker.media.service.PhotoService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * 인스타그램이 가져가는 공개 미디어. 렌더링된 JPEG만 내려주고 원본은 서빙하지 않는다.
 * rerender 가 같은 토큰의 파일을 덮어쓰므로 immutable 캐시 대신 ETag 재검증을 쓴다.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

	private final PhotoService photoService;

	@GetMapping("/{token}.jpg")
	public ResponseEntity<byte[]> get(@PathVariable("token") String token, WebRequest request) {
		return photoService.readRendered(token).map(bytes -> {
			String etag = "\"" + sha256(bytes) + "\"";
			CacheControl cache = CacheControl.noCache().cachePublic();
			if (request.checkNotModified(etag)) {
				return ResponseEntity.status(304).eTag(etag).cacheControl(cache).<byte[]>build();
			}
			return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).eTag(etag).cacheControl(cache).body(bytes);
		}).orElseGet(() -> ResponseEntity.notFound().build());
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
