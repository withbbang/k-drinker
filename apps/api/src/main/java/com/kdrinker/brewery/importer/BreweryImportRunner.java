package com.kdrinker.brewery.importer;

import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** --app.brewery-import.file=경로 가 있을 때만 CSV를 적재하고 앱을 끝낸다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("app.brewery-import.file")
public class BreweryImportRunner implements ApplicationRunner {

	private final BreweryCsvImporter importer;
	private final Environment environment;
	private final ApplicationContext context;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		Path file = Path.of(environment.getRequiredProperty("app.brewery-import.file"));
		Charset charset = Charset.forName(environment.getProperty("app.brewery-import.charset", "UTF-8"));
		try (Reader reader = Files.newBufferedReader(file, charset)) {
			ImportReport report = importer.importFrom(reader, file.getFileName().toString());
			log.info("양조장 적재 완료: {}", report);
		}
		System.exit(SpringApplication.exit(context, () -> 0));
	}

}
