package com.kdrinker.analysis.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.media.ImageDecoder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeImagesTest {

	private final ClaudeImages images = new ClaudeImages(new ImageDecoder());

	@TempDir
	Path dir;

	@Test
	void 큰_사진은_긴_변이_1568로_줄어든다() throws IOException {
		// given
		Path file = write(jpeg(4000, 3000, null));

		// when
		BufferedImage out = read(images.prepare(file));

		// then
		assertThat(out.getWidth()).isEqualTo(1568);
		assertThat(out.getHeight()).isEqualTo(1176);
	}

	@Test
	void 작은_사진은_키우지_않는다() throws IOException {
		// given
		Path file = write(jpeg(800, 600, null));

		// when
		BufferedImage out = read(images.prepare(file));

		// then
		assertThat(out.getWidth()).isEqualTo(800);
		assertThat(out.getHeight()).isEqualTo(600);
	}

	@Test
	void EXIF_6은_회전해서_세로가_된다() throws IOException {
		// given
		Path file = write(jpeg(400, 300, 6));

		// when
		BufferedImage out = read(images.prepare(file));

		// then
		assertThat(out.getHeight()).isGreaterThan(out.getWidth());
		assertThat(out.getWidth()).isEqualTo(300);
		assertThat(out.getHeight()).isEqualTo(400);
	}

	private Path write(byte[] bytes) throws IOException {
		return Files.write(dir.resolve("a.jpg"), bytes);
	}

	private static BufferedImage read(byte[] bytes) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	private static byte[] jpeg(int w, int h, Integer orientation) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpg", out);
		byte[] jpeg = out.toByteArray();
		if (orientation == null) {
			return jpeg;
		}
		byte[] app1 = {
				(byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
				'E', 'x', 'i', 'f', 0, 0,
				'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
				0x00, 0x01,
				0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.byteValue(), 0x00, 0x00,
				0x00, 0x00, 0x00, 0x00
		};
		ByteArrayOutputStream res = new ByteArrayOutputStream();
		res.write(jpeg, 0, 2);
		res.write(app1, 0, app1.length);
		res.write(jpeg, 2, jpeg.length - 2);
		return res.toByteArray();
	}
}
