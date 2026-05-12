package com.example.quickbooksimporter.domain;

import java.util.List;

public record CsvCompareResult(
        String file1Name,
        String file2Name,
        int totalKeysChecked,
        int matchedKeys,
        int mismatchedKeys,
        int missingKeys,
        List<CsvCompareMismatchRow> mismatchRows,
        String file1MismatchCsv,
        String file2MismatchCsv,
        String combinedMismatchCsv) {
}
