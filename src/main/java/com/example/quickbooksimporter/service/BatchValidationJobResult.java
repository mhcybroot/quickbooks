package com.example.quickbooksimporter.service;

import java.util.List;

public record BatchValidationJobResult(
        List<BatchValidationItemResult> items,
        List<String> dependencyWarnings,
        int validatedFiles,
        int runnableFiles) {

    public record BatchValidationItemResult(
            int position,
            ImportPreviewJobResult previewResult,
            String suggestedProfileName) {
    }
}
