package com.example.quickbooksimporter.domain;

public record CsvCompareMappingPair(int index, String file1Header, String file2Header) {

    public String indexLabel() {
        int value = Math.max(1, index);
        return String.valueOf((char) ('A' + value - 1));
    }
}
