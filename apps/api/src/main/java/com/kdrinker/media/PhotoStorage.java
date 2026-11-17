package com.kdrinker.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 사진 파일 저장소. 경로는 모두 여기서 조립하고, 사용자 입력 문자열은 경로에 쓰지 않는다.
 * DB에는 MEDIA_DIR 기준 상대 경로를 저장한다.
 */
@Component
public class PhotoStorage {

	public static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

	private final Path baseDir;

	public PhotoStorage(@Value("${app.media.dir}") String mediaDir) {
		this.baseDir = Path.of(mediaDir).toAbsolutePath().normalize();
	}

	private static final Pattern STAGED_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

	/** DB에 저장하는 원본 상대 경로: original/{postId}/{position}.{jpg|png} */
	public static String originalPath(Long postId, int position, String format) {
		if (!"jpg".equals(format) && !"png".equals(format)) {
			throw new IllegalArgumentException("지원하지 않는 형식");
		}
		return "original/" + postId + "/" + position + "." + format;
	}

	/** DB에 저장하는 렌더링 상대 경로: rendered/{token}.jpg */
	public static String renderedPath(String token) {
		if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
			throw new IllegalArgumentException("토큰 형식이 올바르지 않음");
		}
		return "rendered/" + token + ".jpg";
	}

	/** {MEDIA_DIR}/original/{postId}/{position}.{jpg|png} 에 원본을 쓰고 상대 경로를 돌려준다. */
	public String saveOriginal(Long postId, int position, String format, byte[] bytes) {
		return write(originalPath(postId, position, format), bytes);
	}

	/** {MEDIA_DIR}/rendered/{token}.jpg 에 렌더링 결과를 쓰고(덮어쓰기 가능) 상대 경로를 돌려준다. */
	public String saveRendered(String token, byte[] bytes) {
		return write(renderedPath(token), bytes);
	}

	/** {MEDIA_DIR}/tmp/{stagingId}/{name} 에 임시로 쓴다. 최종 위치로는 {@link #promote} 로 옮긴다. */
	public String saveStaged(String stagingId, String name, byte[] bytes) {
		if (stagingId == null || !TOKEN_PATTERN.matcher(stagingId).matches()
				|| name == null || !STAGED_NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("스테이징 경로가 올바르지 않음");
		}
		return write("tmp/" + stagingId + "/" + name, bytes);
	}

	/** 스테이징 파일을 최종 경로로 원자적으로 옮긴다(같은 MEDIA_DIR 안이므로 rename). */
	public String promote(String stagedPath, String finalPath) {
		if (!stagedPath.startsWith("tmp/")) {
			throw new IllegalArgumentException("스테이징 경로가 아님");
		}
		Path source = resolve(stagedPath);
		Path target = resolve(finalPath);
		try {
			Files.createDirectories(target.getParent());
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return finalPath;
	}

	/** 스테이징 디렉터리를 통째로 지운다. 실패는 무시한다. */
	public void deleteStagingQuietly(String stagingId) {
		if (stagingId == null || !TOKEN_PATTERN.matcher(stagingId).matches()) {
			return;
		}
		deleteTreeQuietly(resolve("tmp/" + stagingId));
	}

	/** original/{postId}/ 디렉터리를 통째로 지운다. 없거나 실패해도 무시한다. */
	public void deletePostOriginalDirQuietly(Long postId) {
		if (postId == null) {
			return;
		}
		deleteTreeQuietly(resolve("original/" + postId));
	}

	/** tmp/ 아래 수정 시각이 cutoff 보다 앞선 스테이징 디렉터리를 지우고 지운 개수를 돌려준다. 실패한 것은 건너뛴다. */
	public int deleteStagingOlderThan(Instant cutoff) {
		Path tmp = resolve("tmp");
		if (!Files.isDirectory(tmp)) {
			return 0;
		}
		int deleted = 0;
		try (var children = Files.list(tmp)) {
			for (Path child : children.filter(Files::isDirectory).toList()) {
				try {
					if (Files.getLastModifiedTime(child).toInstant().isBefore(cutoff)) {
						deleteTreeQuietly(child);
						if (!Files.exists(child)) {
							deleted++;
						}
					}
				} catch (IOException e) {
					// 이 폴더는 건너뛴다.
				}
			}
		} catch (IOException | RuntimeException e) {
			// 정리 실패는 무시한다.
		}
		return deleted;
	}

	private static void deleteTreeQuietly(Path dir) {
		try {
			if (!Files.exists(dir)) {
				return;
			}
			try (var paths = Files.walk(dir)) {
				paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException e) {
						// 정리 실패는 무시한다.
					}
				});
			}
		} catch (IOException | RuntimeException e) {
			// 정리 실패는 무시한다.
		}
	}

	/** 상대 경로를 MEDIA_DIR 안의 절대 경로로 바꾼다. 파일을 직접 스트림으로 읽어야 하는 쪽(분석 이미지 준비)이 쓴다. */
	public Path locate(String relativePath) {
		return resolve(relativePath);
	}

	public byte[] read(String relativePath) {
		try {
			return Files.readAllBytes(resolve(relativePath));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void deleteQuietly(String relativePath) {
		try {
			Files.deleteIfExists(resolve(relativePath));
		} catch (IOException | RuntimeException e) {
			// 정리 실패는 무시한다.
		}
	}

	private String write(String relativePath, byte[] bytes) {
		Path target = resolve(relativePath);
		try {
			Files.createDirectories(target.getParent());
			// 같은 디렉터리의 임시 파일에 쓴 뒤 원자적으로 교체해, 읽는 쪽이 잘린 파일을 보지 않게 한다.
			Path temp = Files.createTempFile(target.getParent(), ".tmp-", ".part");
			try {
				Files.write(temp, bytes);
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} finally {
				Files.deleteIfExists(temp);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return relativePath;
	}

	private Path resolve(String relativePath) {
		Path resolved = baseDir.resolve(relativePath).normalize();
		if (!resolved.startsWith(baseDir)) {
			throw new IllegalArgumentException("MEDIA_DIR 밖의 경로");
		}
		return resolved;
	}

}
