package com.example.quickbooksimporter.domain;

import java.util.Map;

public record ParsedCsvRow(int rowNumber, Map<String, String> values) {
}
