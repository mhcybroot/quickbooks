package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.ParsedCsvRow;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Service;

@Service
public class InvoiceCsvParser {

    public ParsedCsvDocument parse(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setAllowMissingColumnNames(true)
                     .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .build()
                     .parse(reader)) {
            List<String> headers = normalizeHeaders(parser.getHeaderNames());
            List<ParsedCsvRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int index = 0; index < headers.size(); index++) {
                    values.put(headers.get(index), index < record.size() ? record.get(index) : "");
                }
                rows.add(new ParsedCsvRow((int) record.getRecordNumber() + 1, values));
            }
            return new ParsedCsvDocument(headers, rows);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to parse CSV", exception);
        }
    }

    private List<String> normalizeHeaders(List<String> rawHeaders) {
        List<String> normalized = new ArrayList<>(rawHeaders.size());
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int index = 0; index < rawHeaders.size(); index++) {
            String raw = rawHeaders.get(index);
            String base = (raw == null || raw.isBlank()) ? "_unnamed_column" : raw.trim();
            int count = seen.getOrDefault(base, 0);
            seen.put(base, count + 1);
            normalized.add(count == 0 ? base : base + "_" + (count + 1));
        }
        return normalized;
    }
}
