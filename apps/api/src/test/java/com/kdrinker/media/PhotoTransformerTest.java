package com.kdrinker.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdrinker.post.entity.FitMode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoTransformerTest {

	private final PhotoTransformer transformer = new PhotoTransformer();

	@Test
	void 어떤_비율이든_1080x1350이다() throws IOException {
		// given
		int[][] sizes = {{4000, 3000}, {1080, 1920}, {3000, 500}, {400, 300}};

		for (int[] size : sizes) {
			for (FitMode mode : FitMode.values()) {
				// when
				BufferedImage out = decode(transformer.render(solid(size[0], size[1], Color.GREEN), null, mode));

				// then
				assertThat(out.getWidth()).as("%dx%d %s", size[0], size[1], mode).isEqualTo(1080);
				assertThat(out.getHeight()).as("%dx%d %s", size[0], size[1], mode).isEqualTo(1350);
			}
		}
	}

	@Test
	void 극단적으로_가늘거나_납작한_원본도_1080x1350이다() throws IOException {
		// given
		int[][] sizes = {{3000, 1}, {1, 3000}};

		for (int[] size : sizes) {
			for (FitMode mode : FitMode.values()) {
				// when
				BufferedImage out = decode(transformer.render(solid(size[0], size[1], Color.GREEN), null, mode));

				// then
				assertThat(out.getWidth()).as("%dx%d %s", size[0], size[1], mode).isEqualTo(1080);
				assertThat(out.getHeight()).as("%dx%d %s", size[0], size[1], mode).isEqualTo(1350);
			}
		}
	}

	@Test
	void focus를_중심으로_자른다() throws IOException {
		// given: 왼쪽 절반 빨강, 오른쪽 절반 파랑
		BufferedImage src = redBlue(1000, 500);

		// when
		BufferedImage out = decode(transformer.render(src, new FocusBox(0.75, 0.25, 0.2, 0.5), FitMode.CROP));

		// then
		assertThat(isBlue(out.getRGB(540, 675))).isTrue();
	}

	@Test
	void focus가_가장자리면_경계_안으로_밀어_넣는다() throws IOException {
		// given
		BufferedImage src = redBlue(1000, 500);

		// when
		BufferedImage out = decode(transformer.render(src, new FocusBox(0.95, 0.25, 0.1, 0.5), FitMode.CROP));

		// then
		assertThat(out.getWidth()).isEqualTo(1080);
		assertThat(out.getHeight()).isEqualTo(1350);
		assertThat(isBlue(out.getRGB(1079, 675))).isTrue();
	}

	@Test
	void PAD는_원본_전체를_보존하고_위아래에_배경을_채운다() throws IOException {
		// given: 가로로 긴 빨강 이미지
		BufferedImage src = solid(1000, 500, Color.RED);

		// when
		BufferedImage out = decode(transformer.render(src, null, FitMode.PAD));

		// then: 중앙은 원본, 맨 위는 흐린 배경이지만 비어있지 않다
		assertThat(isRed(out.getRGB(540, 675))).isTrue();
		assertThat(isRed(out.getRGB(540, 5))).isTrue();
	}

	@Test
	void 큰_원본을_줄여도_세밀한_줄무늬가_회색으로_평균된다() throws IOException {
		// given: 1픽셀 간격 흑백 세로줄무늬 4320x5400 (4배 축소)
		BufferedImage src = new BufferedImage(4320, 5400, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				src.setRGB(x, y, x % 2 == 0 ? 0xFFFFFF : 0x000000);
			}
		}

		// when
		BufferedImage out = decode(transformer.render(src, null, FitMode.CROP));

		// then: 에일리어싱이면 흑/백으로 갈리고, 제대로 줄이면 중간 회색이다
		int v = out.getRGB(540, 675) & 0xFF;
		assertThat(v).isBetween(90, 165);
	}

	private static BufferedImage decode(byte[] jpeg) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(jpeg));
	}

	private static BufferedImage solid(int w, int h, Color color) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, w, h);
		g.dispose();
		return img;
	}

	private static BufferedImage redBlue(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, w / 2, h);
		g.setColor(Color.BLUE);
		g.fillRect(w / 2, 0, w - w / 2, h);
		g.dispose();
		return img;
	}

	private static boolean isBlue(int rgb) {
		return (rgb & 0xFF) > 180 && ((rgb >> 16) & 0xFF) < 80;
	}

	private static boolean isRed(int rgb) {
		return ((rgb >> 16) & 0xFF) > 180 && (rgb & 0xFF) < 80;
	}

}
