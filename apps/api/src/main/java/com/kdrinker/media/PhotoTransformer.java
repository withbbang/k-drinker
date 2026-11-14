package com.kdrinker.media;

import com.kdrinker.post.entity.FitMode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/** 사진을 인스타그램 캐러셀용 1080×1350(4:5) JPEG로 변환한다. */
@Component
public class PhotoTransformer {

	static final int WIDTH = 1080;
	static final int HEIGHT = 1350;
	private static final double RATIO = 4.0 / 5.0;
	private static final float JPEG_QUALITY = 0.9f;

	public byte[] render(BufferedImage src, FocusBox focus, FitMode mode) {
		BufferedImage opaque = toOpaque(src);
		BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			if (mode == FitMode.PAD) {
				drawPad(g, opaque);
			} else {
				drawCrop(g, opaque, focus);
			}
		} finally {
			g.dispose();
		}
		return writeJpeg(out);
	}

	private void drawCrop(Graphics2D g, BufferedImage src, FocusBox focus) {
		int[] r = cropRect(src.getWidth(), src.getHeight(), focus);
		g.drawImage(downscale(src.getSubimage(r[0], r[1], r[2] - r[0], r[3] - r[1]), WIDTH, HEIGHT), 0, 0, null);
	}

	/** 반환값은 {x1, y1, x2, y2}. 창이 원본 밖으로 나가면 경계 안으로 민다. */
	private int[] cropRect(int w, int h, FocusBox focus) {
		double cw;
		double ch;
		if ((double) w / h > RATIO) {
			ch = h;
			cw = h * RATIO;
		} else {
			cw = w;
			ch = w / RATIO;
		}
		double cx = focus != null ? (focus.x() + focus.w() / 2) * w : w / 2.0;
		double cy = focus != null ? (focus.y() + focus.h() / 2) * h : h / 2.0;
		double left = clamp(cx - cw / 2, 0, w - cw);
		double top = clamp(cy - ch / 2, 0, h - ch);
		int x1 = (int) Math.round(left);
		int y1 = (int) Math.round(top);
		int x2 = Math.min(w, (int) Math.round(left + cw));
		int y2 = Math.min(h, (int) Math.round(top + ch));
		return new int[] {x1, y1, x2, y2};
	}

	private void drawPad(Graphics2D g, BufferedImage src) {
		// 배경: cover로 채운 뒤 작게 줄였다가 키워 흐리게 만든다.
		int[] r = cropRect(src.getWidth(), src.getHeight(), null);
		BufferedImage small = new BufferedImage(WIDTH / 20, HEIGHT / 20, BufferedImage.TYPE_INT_RGB);
		Graphics2D sg = small.createGraphics();
		sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		sg.drawImage(src, 0, 0, small.getWidth(), small.getHeight(), r[0], r[1], r[2], r[3], null);
		sg.dispose();
		g.drawImage(small, 0, 0, WIDTH, HEIGHT, null);

		// 전경: contain
		double scale = Math.min((double) WIDTH / src.getWidth(), (double) HEIGHT / src.getHeight());
		int fw = Math.max(1, (int) Math.round(src.getWidth() * scale));
		int fh = Math.max(1, (int) Math.round(src.getHeight() * scale));
		g.drawImage(downscale(src, fw, fh), (WIDTH - fw) / 2, (HEIGHT - fh) / 2, null);
	}

	/** 목표 크기의 2배 이상인 동안 bilinear로 절반씩 줄이고, 마지막에 bicubic으로 정확한 크기에 맞춘다. */
	static BufferedImage downscale(BufferedImage src, int tw, int th) {
		BufferedImage cur = src;
		while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
			cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		return resize(cur, tw, th, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	}

	private static BufferedImage resize(BufferedImage src, int w, int h, Object interpolation) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return dst;
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	/** 알파 채널이 있으면 흰 배경 위로 합성한다. */
	private BufferedImage toOpaque(BufferedImage src) {
		if (!src.getColorModel().hasAlpha()) {
			return src;
		}
		BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rgb.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return rgb;
	}

	private byte[] writeJpeg(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			writer.dispose();
		}
	}

}
