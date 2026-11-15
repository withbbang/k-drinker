package com.kdrinker.brewery.importer;

import java.util.List;

public record ImportReport(int inserted, int updated, int skipped, List<String> skippedReasons) {
}
