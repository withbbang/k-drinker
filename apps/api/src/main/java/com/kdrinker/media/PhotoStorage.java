package com.kdrinker.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

	/** {MEDIA_DIR}/original/{postId}/{position}.{jpg|png} 에 원본을 쓰고 상대 경로를 돌려준다. */
	public String saveOriginal(Long postId, int position, String format, byte[] bytes) {
		if (!"jpg".equals(format) && !"png".equals(format)) {
			throw new IllegalArgumentException("지원하지 않는 형식");
		}
		return write("original/" + postId + "/" + position + "." + format, bytes);
	}

	/** {MEDIA_DIR}/rendered/{token}.jpg 에 렌더링 결과를 쓰고(덮어쓰기 가능) 상대 경로를 돌려준다. */
	public String saveRendered(String token, byte[] bytes) {
		if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
			throw new IllegalArgumentException("토큰 형식이 올바르지 않음");
		}
		return write("rendered/" + token + ".jpg", bytes);
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
