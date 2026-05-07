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
import org.springframework.stereotype.Service;

@Service
public class InvoiceCsvParser {

    public ParsedCsvDocument parse(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .build()
                     .parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            List<ParsedCsvRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                for (String header : headers) {
                    values.put(header, record.get(header));
                }
                rows.add(new ParsedCsvRow((int) record.getRecordNumber() + 1, values));
            }
            return new ParsedCsvDocument(headers, rows);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to parse CSV", exception);
        }
    }
}
