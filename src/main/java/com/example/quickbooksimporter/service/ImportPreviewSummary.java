package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import java.util.List;

public record ImportPreviewSummary(
        EntityType entityType,
        String sourceFileName,
        List<String> headers,
        int totalRows,
        int readyRows,
        int invalidRows,
        int duplicateRows,
        String exportCsv,
        String suggestedProfileName,
        List<String> warnings,
        Object rawPreview) {

    public boolean hasBlockingIssues() {
        return invalidRows > 0;
    }

    public ImportRunStatusSummary runStatusSummary() {
        if (invalidRows > 0) {
            return ImportRunStatusSummary.BLOCKED;
        }
        return readyRows > 0 ? ImportRunStatusSummary.READY : ImportRunStatusSummary.EMPTY;
    }

    public static int duplicateCount(List<? extends ImportPreviewStatusRow> rows) {
        return (int) rows.stream().filter(row -> row.status() == ImportRowStatus.DUPLICATE).count();
    }

    public enum ImportRunStatusSummary {
        EMPTY,
        READY,
        BLOCKED
    }

    public interface ImportPreviewStatusRow {
        ImportRowStatus status();
    }
}
