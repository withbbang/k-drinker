package com.kdrinker.analysis.image;

import com.kdrinker.media.ImageDecoder;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/** Claude에 보낼 원본 사진을 준비한다. EXIF 회전을 적용하므로 FocusBox와 같은 좌표계(회전 적용 원본 기준)다. */
@Component
public class ClaudeImages {

	static final int MAX_LONG_SIDE = 1568;
	private static final float JPEG_QUALITY = 0.85f;

	private final ImageDecoder decoder;

	public ClaudeImages(ImageDecoder decoder) {
		this.decoder = decoder;
	}

	/** 한 장씩 읽고 처리한다. 긴 변 1568px 이하 JPEG(품질 0.85)를 돌려준다. 작은 사진은 키우지 않는다. */
	public byte[] prepare(Path original) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(original);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		BufferedImage src = decoder.decode(bytes);
		return writeJpeg(fit(src));
	}

	private static BufferedImage fit(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		int longSide = Math.max(w, h);
		double scale = longSide > MAX_LONG_SIDE ? (double) MAX_LONG_SIDE / longSide : 1.0;
		int tw = Math.max(1, (int) Math.round(w * scale));
		int th = Math.max(1, (int) Math.round(h * scale));
		BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(java.awt.Color.WHITE);
			g.fillRect(0, 0, tw, th);
			g.drawImage(src, 0, 0, tw, th, null);
		} finally {
			g.dispose();
		}
		return out;
	}

	private static byte[] writeJpeg(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.setOutput(ios);
			writer.write(null, new IIOImage(image, null, null), param);
			ios.flush();
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			writer.dispose();
		}
	}
}
