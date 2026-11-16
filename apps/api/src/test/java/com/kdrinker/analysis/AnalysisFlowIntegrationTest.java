package com.kdrinker.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.kdrinker.analysis.brewery.BreweryResearcher;
import com.kdrinker.analysis.label.LabelReader;
import com.kdrinker.analysis.label.LabelResult;
import com.kdrinker.caption.CaptionDraft;
import com.kdrinker.caption.CaptionWriter;
import com.kdrinker.caption.DraftTag;
import com.kdrinker.caption.TagCategory;
import com.kdrinker.post.entity.PostSource;
import com.kdrinker.post.entity.PostStatus;
import com.kdrinker.post.repository.PostRepository;
import com.kdrinker.post.service.PostIntakeService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 접수 → 커밋 뒤 PostReceivedEvent → 비동기 리스너(analysisExecutor) → 분석 → PENDING_REVIEW 를 끝까지 잇는다.
 * Claude 경계(라벨·양조장 조사·캡션)만 목이다. 기다림은 Awaitility로 상한(10초)을 둔다.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
class AnalysisFlowIntegrationTest {

	private static final Path MEDIA_DIR = createMediaDir();

	@DynamicPropertySource
	static void mediaDir(DynamicPropertyRegistry registry) {
		registry.add("app.media.dir", MEDIA_DIR::toString);
	}

	@Autowired
	private PostIntakeService intake;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private LabelReader labelReader;

	@MockitoBean
	private BreweryResearcher breweryResearcher;

	@MockitoBean
	private CaptionWriter captionWriter;

	private static Path createMediaDir() {
		try {
			return Files.createTempDirectory("analysis-flow-media");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static byte[] jpeg() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), "jpg", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@BeforeEach
	@AfterEach
	void cleanTables() {
		jdbc.update("DELETE FROM post_photo");
		jdbc.update("DELETE FROM post");
		jdbc.update("DELETE FROM drink");
		jdbc.update("DELETE FROM brewery_alias");
		jdbc.update("DELETE FROM brewery");
	}

	@Test
	void 접수가_커밋되면_분석_실행기에서_분석해_검토_대기가_된다() {
		// given
		AtomicReference<String> labelThread = new AtomicReference<>();
		when(labelReader.read(anyList(), any())).thenAnswer(invocation -> {
			labelThread.set(Thread.currentThread().getName());
			return new LabelResult("새술", "막걸리", new BigDecimal("6.0"), 750, "새술양조장", null, "달콤함", "전",
					List.of());
		});
		when(breweryResearcher.research(any(), any())).thenReturn(Optional.empty());
		when(captionWriter.write(any())).thenReturn(new CaptionDraft("오늘의 한 잔", "새술을 마셨어요.", "달콤했어요.",
				List.of(new DraftTag("#새술", TagCategory.NAME))));

		// when
		Long postId = intake.receive(PostSource.TELEGRAM, "달달함", null, List.of(jpeg())).getId();

		// then
		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
				.until(() -> postRepository.findById(postId).orElseThrow().getStatus() == PostStatus.PENDING_REVIEW);
		assertThat(labelThread.get()).startsWith("analysis-");
		assertThat(postRepository.findById(postId).orElseThrow().getCaption()).contains("새술을 마셨어요.");
	}
}
