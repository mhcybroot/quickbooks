package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.CsvCompareMappingPair;
import com.example.quickbooksimporter.domain.CsvCompareMismatchRow;
import com.example.quickbooksimporter.domain.CsvCompareRequest;
import com.example.quickbooksimporter.domain.CsvCompareResult;
import com.example.quickbooksimporter.domain.ParsedCsvRow;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class CsvCompareService {

    private final InvoiceCsvParser parser;

    public CsvCompareService(InvoiceCsvParser parser) {
        this.parser = parser;
    }

    public CsvCompareResult preview(CsvCompareRequest request) {
        validateRequest(request);
        ParsedCsvDocument file1 = parser.parse(new ByteArrayInputStream(request.file1Bytes()));
        ParsedCsvDocument file2 = parser.parse(new ByteArrayInputStream(request.file2Bytes()));

        CsvCompareMappingPair keyPair = request.mappingPairs().getFirst();
        List<CsvCompareMismatchRow> mismatches = new ArrayList<>();

        Map<String, ParsedCsvRow> file1ByKey = new LinkedHashMap<>();
        Map<String, ParsedCsvRow> file2ByKey = new LinkedHashMap<>();
        Set<String> duplicateKeys = new LinkedHashSet<>();

        indexRows(file1.rows(), keyPair.file1Header(), file1ByKey, duplicateKeys, mismatches, "DUPLICATE_KEY_IN_FILE1", true);
        indexRows(file2.rows(), keyPair.file2Header(), file2ByKey, duplicateKeys, mismatches, "DUPLICATE_KEY_IN_FILE2", false);

        Set<String> unionKeys = new LinkedHashSet<>();
        unionKeys.addAll(file1ByKey.keySet());
        unionKeys.addAll(file2ByKey.keySet());

        int matchedKeys = 0;
        int missingKeys = 0;
        int mismatchedKeys = 0;

        for (String key : unionKeys) {
            if (duplicateKeys.contains(key)) {
                mismatchedKeys++;
                continue;
            }
            ParsedCsvRow row1 = file1ByKey.get(key);
            ParsedCsvRow row2 = file2ByKey.get(key);

            if (row1 == null || row2 == null) {
                missingKeys++;
                mismatchedKeys++;
                mismatches.add(new CsvCompareMismatchRow(
                        key,
                        row1 == null ? null : row1.rowNumber(),
                        row2 == null ? null : row2.rowNumber(),
                        request.mappingPairs().getFirst().indexLabel(),
                        readValue(row1, keyPair.file1Header()),
                        readValue(row2, keyPair.file2Header()),
                        "MISSING_MATCH"));
                continue;
            }

            boolean keyHasMismatch = false;
            for (CsvCompareMappingPair pair : request.mappingPairs()) {
                String value1 = normalize(readValue(row1, pair.file1Header()));
                String value2 = normalize(readValue(row2, pair.file2Header()));
                if (!Objects.equals(value1, value2)) {
                    keyHasMismatch = true;
                    mismatches.add(new CsvCompareMismatchRow(
                            key,
                            row1.rowNumber(),
                            row2.rowNumber(),
                            pair.indexLabel(),
                            value1,
                            value2,
                            "VALUE_MISMATCH"));
                }
            }

            if (keyHasMismatch) {
                mismatchedKeys++;
            } else {
                matchedKeys++;
            }
        }

        ExportBundle exports = buildExports(file1.headers(), file2.headers(), file1ByKey, file2ByKey, mismatches);

        return new CsvCompareResult(
                request.file1Name(),
                request.file2Name(),
                unionKeys.size(),
                matchedKeys,
                mismatchedKeys,
                missingKeys,
                mismatches,
                exports.file1Csv(),
                exports.file2Csv(),
                exports.combinedCsv());
    }

    private void validateRequest(CsvCompareRequest request) {
        if (request == null || request.file1Bytes() == null || request.file2Bytes() == null) {
            throw new IllegalArgumentException("Both CSV files are required");
        }
        if (request.mappingPairs() == null || request.mappingPairs().isEmpty()) {
            throw new IllegalArgumentException("At least one mapping pair is required");
        }
        request.mappingPairs().forEach(pair -> {
            if (StringUtils.isBlank(pair.file1Header()) || StringUtils.isBlank(pair.file2Header())) {
                throw new IllegalArgumentException("Each mapping pair must select headers from both files");
            }
        });
    }

    private void indexRows(List<ParsedCsvRow> rows,
                           String keyHeader,
                           Map<String, ParsedCsvRow> byKey,
                           Set<String> duplicateKeys,
                           List<CsvCompareMismatchRow> mismatches,
                           String duplicateReason,
                           boolean isFile1) {
        for (ParsedCsvRow row : rows) {
            String key = normalize(readValue(row, keyHeader));
            if (byKey.containsKey(key)) {
                duplicateKeys.add(key);
                ParsedCsvRow first = byKey.get(key);
                mismatches.add(new CsvCompareMismatchRow(
                        key,
                        isFile1 ? first.rowNumber() : null,
                        isFile1 ? null : first.rowNumber(),
                        "A",
                        isFile1 ? key : "",
                        isFile1 ? "" : key,
                        duplicateReason));
                mismatches.add(new CsvCompareMismatchRow(
                        key,
                        isFile1 ? row.rowNumber() : null,
                        isFile1 ? null : row.rowNumber(),
                        "A",
                        isFile1 ? key : "",
                        isFile1 ? "" : key,
                        duplicateReason));
                continue;
            }
            byKey.put(key, row);
        }
    }

    private String readValue(ParsedCsvRow row, String header) {
        if (row == null || header == null) {
            return "";
        }
        return row.values().getOrDefault(header, "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private ExportBundle buildExports(List<String> file1Headers,
                                      List<String> file2Headers,
                                      Map<String, ParsedCsvRow> file1ByKey,
                                      Map<String, ParsedCsvRow> file2ByKey,
                                      List<CsvCompareMismatchRow> mismatches) {
        List<ExportRow> file1Rows = new ArrayList<>();
        List<ExportRow> file2Rows = new ArrayList<>();
        List<ExportRow> combinedRows = new ArrayList<>();
        Set<String> file1Dedup = new LinkedHashSet<>();
        Set<String> file2Dedup = new LinkedHashSet<>();

        for (CsvCompareMismatchRow mismatch : mismatches) {
            ParsedCsvRow row1 = file1ByKey.get(mismatch.key());
            ParsedCsvRow row2 = file2ByKey.get(mismatch.key());
            String dedupKey1 = mismatch.key() + "|" + mismatch.file1RowNumber() + "|" + mismatch.reason() + "|" + mismatch.mismatchField();
            String dedupKey2 = mismatch.key() + "|" + mismatch.file2RowNumber() + "|" + mismatch.reason() + "|" + mismatch.mismatchField();

            if (mismatch.file1RowNumber() != null && file1Dedup.add(dedupKey1)) {
                file1Rows.add(ExportRow.fromMismatch(file1Headers, row1, mismatch));
            }
            if (mismatch.file2RowNumber() != null && file2Dedup.add(dedupKey2)) {
                file2Rows.add(ExportRow.fromMismatch(file2Headers, row2, mismatch));
            }
            combinedRows.add(ExportRow.combined(mismatch));
        }

        return new ExportBundle(
                toSourceCsv(file1Headers, file1Rows),
                toSourceCsv(file2Headers, file2Rows),
                toCombinedCsv(combinedRows));
    }

    private String toSourceCsv(List<String> sourceHeaders, List<ExportRow> rows) {
        List<String> headers = new ArrayList<>(sourceHeaders);
        headers.add("__compare_key");
        headers.add("__compare_reason");
        headers.add("__compare_field");
        headers.add("__compare_file1_value");
        headers.add("__compare_file2_value");
        headers.add("__compare_file1_row");
        headers.add("__compare_file2_row");
        try {
            StringWriter writer = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers.toArray(String[]::new)))) {
                for (ExportRow row : rows) {
                    List<String> values = new ArrayList<>();
                    for (String sourceHeader : sourceHeaders) {
                        values.add(row.originalValues().getOrDefault(sourceHeader, ""));
                    }
                    values.add(row.key());
                    values.add(row.reason());
                    values.add(row.mismatchField());
                    values.add(row.file1Value());
                    values.add(row.file2Value());
                    values.add(row.file1RowNumber());
                    values.add(row.file2RowNumber());
                    printer.printRecord(values);
                }
            }
            return writer.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export mismatch CSV", exception);
        }
    }

    private String toCombinedCsv(List<ExportRow> rows) {
        try {
            StringWriter writer = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                    "__compare_key",
                    "__compare_reason",
                    "__compare_field",
                    "__compare_file1_value",
                    "__compare_file2_value",
                    "__compare_file1_row",
                    "__compare_file2_row"))) {
                for (ExportRow row : rows) {
                    printer.printRecord(
                            row.key(),
                            row.reason(),
                            row.mismatchField(),
                            row.file1Value(),
                            row.file2Value(),
                            row.file1RowNumber(),
                            row.file2RowNumber());
                }
            }
            return writer.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export combined mismatch CSV", exception);
        }
    }

    private record ExportBundle(String file1Csv, String file2Csv, String combinedCsv) {
    }

    private record ExportRow(
            Map<String, String> originalValues,
            String key,
            String reason,
            String mismatchField,
            String file1Value,
            String file2Value,
            String file1RowNumber,
            String file2RowNumber) {
        private static ExportRow fromMismatch(List<String> headers, ParsedCsvRow sourceRow, CsvCompareMismatchRow mismatch) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String header : headers) {
                values.put(header, sourceRow == null ? "" : sourceRow.values().getOrDefault(header, ""));
            }
            return new ExportRow(
                    values,
                    mismatch.key(),
                    mismatch.reason(),
                    mismatch.mismatchField(),
                    mismatch.file1Value(),
                    mismatch.file2Value(),
                    mismatch.file1RowNumber() == null ? "" : String.valueOf(mismatch.file1RowNumber()),
                    mismatch.file2RowNumber() == null ? "" : String.valueOf(mismatch.file2RowNumber()));
        }

        private static ExportRow combined(CsvCompareMismatchRow mismatch) {
            return new ExportRow(
                    Map.of(),
                    mismatch.key(),
                    mismatch.reason(),
                    mismatch.mismatchField(),
                    mismatch.file1Value(),
                    mismatch.file2Value(),
                    mismatch.file1RowNumber() == null ? "" : String.valueOf(mismatch.file1RowNumber()),
                    mismatch.file2RowNumber() == null ? "" : String.valueOf(mismatch.file2RowNumber()));
        }
    }
}
