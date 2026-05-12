package com.example.quickbooksimporter.domain;

import java.util.List;

public record CsvCompareRequest(
        String file1Name,
        byte[] file1Bytes,
        String file2Name,
        byte[] file2Bytes,
        List<CsvCompareMappingPair> mappingPairs) {
}
