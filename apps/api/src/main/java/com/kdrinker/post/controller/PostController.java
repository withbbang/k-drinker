package com.kdrinker.post.controller;

import com.kdrinker.common.ApiResponse;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.post.dto.PostCreateResponse;
import com.kdrinker.post.entity.Post;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.service.PostIntakeService;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostIntakeService postIntakeService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<PostCreateResponse>> create(
			@RequestParam(name = "photos", required = false) List<MultipartFile> photos,
			@RequestParam(name = "memo", required = false) String memo,
			@RequestParam(name = "rating", required = false) BigDecimal rating,
			@RequestParam("source") PostSource source) {
		List<MultipartFile> files = photos == null ? List.of() : photos;
		PostIntakeService.validate(files.size(), memo, rating);
		List<byte[]> bytes = new ArrayList<>();
		for (MultipartFile file : files) {
			try {
				bytes.add(file.getBytes());
			} catch (IOException e) {
				throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
			}
		}
		Post post = postIntakeService.receive(source, memo, rating, bytes);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(new PostCreateResponse(post.getId(), post.getStatus().name())));
	}

}
