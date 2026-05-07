package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.ParsedCsvRow;
import java.util.List;

public record ParsedCsvDocument(List<String> headers, List<ParsedCsvRow> rows) {
}
