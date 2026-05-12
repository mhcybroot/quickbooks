package com.example.quickbooksimporter.domain;

public record CsvCompareMismatchRow(
        String key,
        Integer file1RowNumber,
        Integer file2RowNumber,
        String mismatchField,
        String file1Value,
        String file2Value,
        String reason) {
}
