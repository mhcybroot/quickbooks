package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import java.util.List;

public record ImportPreviewJobResult(
        EntityType entityType,
        String sourceFileName,
        int totalRows,
        int readyRows,
        int invalidRows,
        int duplicateRows,
        String exportCsv,
        List<String> warnings,
        String rawPreviewJson) {
}
