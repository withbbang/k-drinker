package com.kdrinker.media;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/** JPEG·PNG 바이트를 내용으로 판별해 디코딩하고, EXIF 방향을 적용한다. */
@Component
public class ImageDecoder {

	/** 디코딩 폭탄(작은 파일이 거대한 래스터로 풀리는 경우) 방지용 상한. */
	static final long MAX_PIXELS = 250_000_000L;

	/** 디코딩 결과의 긴 변 상한(출력 1080의 4배). 넘으면 서브샘플링으로 줄여 읽는다. */
	static final int MAX_LONG_SIDE = 4320;

	public BufferedImage decode(byte[] bytes) {
		boolean jpeg = isJpeg(bytes);
		if (!jpeg && !isPng(bytes)) {
			throw unsupported();
		}
		try {
			BufferedImage image = read(bytes);
			return jpeg ? applyOrientation(image, readOrientation(bytes)) : image;
		} catch (BusinessException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			throw unsupported();
		}
	}

	/** 내용(매직 바이트)으로 확장자를 판별한다. "jpg" 또는 "png". 그 외는 MEDIA_UNSUPPORTED_TYPE. */
	public String detectFormat(byte[] bytes) {
		if (isJpeg(bytes)) {
			return "jpg";
		}
		if (isPng(bytes)) {
			return "png";
		}
		throw unsupported();
	}

	private BufferedImage read(byte[] bytes) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw unsupported();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
				if (pixels > MAX_PIXELS) {
					throw unsupported();
				}
				ImageReadParam param = reader.getDefaultReadParam();
				int s = subsampling(reader.getWidth(0), reader.getHeight(0));
				if (s > 1) {
					param.setSourceSubsampling(s, s, 0, 0);
				}
				return reader.read(0, param);
			} finally {
				reader.dispose();
			}
		}
	}

	/** 디코딩된 긴 변이 MAX_LONG_SIDE 이하가 되는 가장 작은 서브샘플링 배율. */
	static int subsampling(int width, int height) {
		int longSide = Math.max(width, height);
		return Math.max(1, (longSide + MAX_LONG_SIDE - 1) / MAX_LONG_SIDE);
	}

	private int readOrientation(byte[] bytes) {
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
			ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
			if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
				return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
			}
		} catch (Exception e) {
			// EXIF가 없거나 읽을 수 없으면 회전하지 않는다.
		}
		return 1;
	}

	private BufferedImage applyOrientation(BufferedImage src, int orientation) {
		int w = src.getWidth();
		int h = src.getHeight();
		AffineTransform t = switch (orientation) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
			case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
			case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
			case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
			case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
			default -> null;
		};
		if (t == null) {
			return src;
		}
		boolean swap = orientation >= 5;
		BufferedImage dst = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		g.drawImage(src, t, null);
		g.dispose();
		return dst;
	}

	private static boolean isJpeg(byte[] b) {
		return b != null && b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
	}

	private static boolean isPng(byte[] b) {
		return b != null && b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
	}

	private static BusinessException unsupported() {
		return new BusinessException(ErrorCode.MEDIA_UNSUPPORTED_TYPE);
	}

}
