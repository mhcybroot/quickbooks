package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quickbooksimporter.domain.CsvCompareMappingPair;
import com.example.quickbooksimporter.domain.CsvCompareRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvCompareServiceTest {

    private final CsvCompareService service = new CsvCompareService(new InvoiceCsvParser());

    @Test
    void returnsNoMismatchWhenAllMappedValuesMatch() {
        CsvCompareRequest request = request(
                "id,name,amount\n1,Acme,100\n2,Globex,200\n",
                "key,client,total\n1,Acme,100\n2,Globex,200\n",
                List.of(
                        new CsvCompareMappingPair(1, "id", "key"),
                        new CsvCompareMappingPair(2, "name", "client"),
                        new CsvCompareMappingPair(3, "amount", "total")));

        var result = service.preview(request);

        assertThat(result.totalKeysChecked()).isEqualTo(2);
        assertThat(result.matchedKeys()).isEqualTo(2);
        assertThat(result.mismatchedKeys()).isZero();
        assertThat(result.missingKeys()).isZero();
        assertThat(result.mismatchRows()).isEmpty();
        assertThat(result.file1MismatchCsv()).contains("__compare_key");
        assertThat(result.file2MismatchCsv()).contains("__compare_key");
        assertThat(result.combinedMismatchCsv()).contains("__compare_key");
    }

    @Test
    void reportsMissingMatchWhenAKeyNotPresentInFile2() {
        CsvCompareRequest request = request(
                "id,name\n1,Acme\n2,Globex\n",
                "key,client\n1,Acme\n",
                List.of(
                        new CsvCompareMappingPair(1, "id", "key"),
                        new CsvCompareMappingPair(2, "name", "client")));

        var result = service.preview(request);

        assertThat(result.totalKeysChecked()).isEqualTo(2);
        assertThat(result.matchedKeys()).isEqualTo(1);
        assertThat(result.missingKeys()).isEqualTo(1);
        assertThat(result.mismatchedKeys()).isEqualTo(1);
        assertThat(result.mismatchRows()).anySatisfy(row -> {
            assertThat(row.key()).isEqualTo("2");
            assertThat(row.reason()).isEqualTo("MISSING_MATCH");
            assertThat(row.mismatchField()).isEqualTo("A");
        });
        assertThat(result.file1MismatchCsv()).contains("2,Globex,2,MISSING_MATCH,A,2,,3,");
        assertThat(result.combinedMismatchCsv()).contains("2,MISSING_MATCH,A,2,,3,");
    }

    @Test
    void reportsMismatchWhenBValueDiffersWithSameA() {
        CsvCompareRequest request = request(
                "id,name\n1,Acme\n",
                "key,client\n1,Acme LLC\n",
                List.of(
                        new CsvCompareMappingPair(1, "id", "key"),
                        new CsvCompareMappingPair(2, "name", "client")));

        var result = service.preview(request);

        assertThat(result.matchedKeys()).isZero();
        assertThat(result.mismatchedKeys()).isEqualTo(1);
        assertThat(result.mismatchRows()).singleElement().satisfies(row -> {
            assertThat(row.key()).isEqualTo("1");
            assertThat(row.mismatchField()).isEqualTo("B");
            assertThat(row.reason()).isEqualTo("VALUE_MISMATCH");
        });
        assertThat(result.file1MismatchCsv()).contains("__compare_field");
        assertThat(result.file2MismatchCsv()).contains("__compare_field");
        assertThat(result.combinedMismatchCsv()).contains("1,VALUE_MISMATCH,B,Acme,Acme LLC,2,2");
    }

    @Test
    void handlesMultiplePairsAndBlankCells() {
        CsvCompareRequest request = request(
                "id,name,city,amount\n1,Acme,,100\n",
                "key,client,location,total\n1,Acme,Dhaka,120\n",
                List.of(
                        new CsvCompareMappingPair(1, "id", "key"),
                        new CsvCompareMappingPair(2, "name", "client"),
                        new CsvCompareMappingPair(3, "city", "location"),
                        new CsvCompareMappingPair(4, "amount", "total")));

        var result = service.preview(request);

        assertThat(result.mismatchRows()).hasSize(2);
        assertThat(result.mismatchRows().stream().map(row -> row.mismatchField())).containsExactlyInAnyOrder("C", "D");
        assertThat(result.file1MismatchCsv()).contains("__compare_reason");
        assertThat(result.file2MismatchCsv()).contains("__compare_reason");
    }

    @Test
    void reportsDuplicateKeysDeterministically() {
        CsvCompareRequest request = request(
                "id,name\n1,Acme\n1,Acme 2\n",
                "key,client\n1,Acme\n",
                List.of(
                        new CsvCompareMappingPair(1, "id", "key"),
                        new CsvCompareMappingPair(2, "name", "client")));

        var result = service.preview(request);

        assertThat(result.totalKeysChecked()).isEqualTo(1);
        assertThat(result.matchedKeys()).isZero();
        assertThat(result.mismatchedKeys()).isEqualTo(1);
        assertThat(result.mismatchRows()).anySatisfy(row -> assertThat(row.reason()).isEqualTo("DUPLICATE_KEY_IN_FILE1"));
        assertThat(result.file1MismatchCsv().lines().count()).isEqualTo(3L);
    }

    private CsvCompareRequest request(String file1Csv, String file2Csv, List<CsvCompareMappingPair> pairs) {
        return new CsvCompareRequest(
                "file1.csv",
                file1Csv.getBytes(StandardCharsets.UTF_8),
                "file2.csv",
                file2Csv.getBytes(StandardCharsets.UTF_8),
                pairs);
    }
}
