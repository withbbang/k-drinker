package com.kdrinker.brewery.importer;

import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BreweryAlias;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryAliasRepository;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.common.Names;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 공공데이터 양조장 CSV를 정규화 이름 기준으로 upsert한다. 전체가 한 트랜잭션이다. */
@Component
@RequiredArgsConstructor
public class BreweryCsvImporter {

	private static final Set<String> HEADERS = Set.of("name", "address", "phone", "homepage", "instagram",
			"aliases", "source_url");

	private final BreweryRepository breweryRepository;
	private final BreweryAliasRepository aliasRepository;

	@Transactional
	public ImportReport importFrom(Reader reader, String sourceLabel) {
		int inserted = 0;
		int updated = 0;
		List<String> reasons = new ArrayList<>();
		CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
				.setIgnoreEmptyLines(true).setTrim(true).get();
		try (CSVParser parser = format.parse(reader)) {
			validateHeaders(parser.getHeaderNames(), sourceLabel);
			for (CSVRecord row : parser) {
				String name = value(row, "name");
				String normalized = Names.normalize(name);
				if (name == null || normalized.isEmpty()) {
					reasons.add(sourceLabel + " " + row.getRecordNumber() + "행: name이 비어 있음");
					continue;
				}
				List<Brewery> found = breweryRepository.findByNormalizedName(normalized);
				Brewery brewery;
				if (found.isEmpty()) {
					brewery = breweryRepository.save(Brewery.of(name, normalized, BrewerySource.PUBLIC_DATA));
					inserted++;
				} else {
					brewery = found.stream().min(Comparator.comparing(Brewery::getId)).orElseThrow();
					brewery.markPublicData();
					updated++;
				}
				brewery.updateContact(
						or(value(row, "address"), brewery.getAddress()),
						or(value(row, "phone"), brewery.getPhone()),
						or(value(row, "homepage"), brewery.getHomepage()),
						or(handle(value(row, "instagram")), brewery.getInstagramHandle()),
						brewery.getOtherSns(),
						or(value(row, "source_url"), brewery.getSourceUrl()));
				brewery.markVerified();
				saveAliases(brewery, value(row, "aliases"));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return new ImportReport(inserted, updated, reasons.size(), List.copyOf(reasons));
	}

	private void validateHeaders(List<String> headers, String sourceLabel) {
		List<String> names = headers.stream().map(h -> h == null ? "" : h.replace("﻿", "").trim()).toList();
		for (String h : names) {
			if (!HEADERS.contains(h)) {
				throw new IllegalArgumentException(sourceLabel + ": 알 수 없는 헤더 '" + h + "'");
			}
		}
		if (!names.contains("name")) {
			throw new IllegalArgumentException(sourceLabel + ": name 헤더가 없음");
		}
	}

	private void saveAliases(Brewery brewery, String aliases) {
		if (aliases == null) {
			return;
		}
		for (String raw : aliases.split("\\|")) {
			String alias = Names.normalize(raw);
			if (!alias.isEmpty() && !aliasRepository.existsByBreweryAndNormalizedAlias(brewery, alias)) {
				aliasRepository.save(BreweryAlias.of(brewery, alias));
			}
		}
	}

	private static String value(CSVRecord row, String header) {
		String key = row.isMapped(header) ? header : null;
		if (key == null) {
			// BOM이 붙은 첫 헤더 대응
			key = row.getParser().getHeaderNames().stream()
					.filter(h -> h != null && h.replace("﻿", "").trim().equals(header)).findFirst().orElse(null);
			if (key == null) {
				return null;
			}
		}
		if (!row.isSet(key)) {
			return null;
		}
		String v = row.get(key);
		return v == null || v.isBlank() ? null : v.trim();
	}

	private static String handle(String v) {
		if (v == null) {
			return null;
		}
		String h = v.startsWith("@") ? v.substring(1).trim() : v;
		return h.isEmpty() ? null : h;
	}

	private static String or(String v, String fallback) {
		return v != null ? v : fallback;
	}

}
