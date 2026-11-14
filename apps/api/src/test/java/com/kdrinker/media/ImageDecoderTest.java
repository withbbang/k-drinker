package com.kdrinker.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageDecoderTest {

	private final ImageDecoder decoder = new ImageDecoder();

	@Test
	void EXIF_방향_6이면_90도_회전한다() throws IOException {
		// given
		byte[] jpeg = withOrientation(encode(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "jpg"), 6);

		// when
		BufferedImage decoded = decoder.decode(jpeg);

		// then
		assertThat(decoded.getWidth()).isEqualTo(300);
		assertThat(decoded.getHeight()).isEqualTo(400);
	}

	@Test
	void EXIF_방향_1이면_그대로다() throws IOException {
		// given
		byte[] jpeg = withOrientation(encode(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "jpg"), 1);

		// when
		BufferedImage decoded = decoder.decode(jpeg);

		// then
		assertThat(decoded.getWidth()).isEqualTo(400);
		assertThat(decoded.getHeight()).isEqualTo(300);
	}

	@Test
	void PNG는_받는다() throws IOException {
		// given
		byte[] png = encode(new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB), "png");

		// when
		BufferedImage decoded = decoder.decode(png);

		// then
		assertThat(decoded.getWidth()).isEqualTo(40);
		assertThat(decoded.getHeight()).isEqualTo(30);
	}

	@Test
	void WebP나_텍스트는_MEDIA_UNSUPPORTED_TYPE이다() {
		// given
		byte[] webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
		byte[] text = "hello".getBytes(StandardCharsets.UTF_8);

		// when, then
		assertThatThrownBy(() -> decoder.decode(webp)).isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_UNSUPPORTED_TYPE);
		assertThatThrownBy(() -> decoder.decode(text)).isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_UNSUPPORTED_TYPE);
	}

	@Test
	void 깨진_JPEG는_MEDIA_UNSUPPORTED_TYPE이다() {
		// given
		byte[] broken = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2};

		// when, then
		assertThatThrownBy(() -> decoder.decode(broken)).isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_UNSUPPORTED_TYPE);
	}

	@Test
	void 픽셀_수가_5천만을_넘으면_디코딩하지_않고_거부한다() throws IOException {
		// given: 20000x20000을 주장하는 IHDR만 있는 PNG
		byte[] bomb = pngHeaderOnly(20000, 20000);

		// when, then
		assertThatThrownBy(() -> decoder.decode(bomb)).isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_UNSUPPORTED_TYPE);
	}

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	/** SOI 바로 뒤에 Orientation만 담은 EXIF APP1 세그먼트를 끼워 넣는다. */
	private static byte[] withOrientation(byte[] jpeg, int orientation) {
		byte[] app1 = {
				(byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
				'E', 'x', 'i', 'f', 0, 0,
				'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
				0x00, 0x01,
				0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
				0x00, 0x00, 0x00, 0x00
		};
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		out.write(app1, 0, app1.length);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	private static byte[] pngHeaderOnly(int width, int height) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
		ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
		ihdr.write("IHDR".getBytes(StandardCharsets.US_ASCII));
		ihdr.write(intBytes(width));
		ihdr.write(intBytes(height));
		ihdr.write(new byte[] {8, 2, 0, 0, 0});
		byte[] chunk = ihdr.toByteArray();
		out.write(intBytes(13));
		out.write(chunk);
		CRC32 crc = new CRC32();
		crc.update(chunk);
		out.write(intBytes((int) crc.getValue()));
		return out.toByteArray();
	}

	private static byte[] intBytes(int v) {
		return new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
	}

}
