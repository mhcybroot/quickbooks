package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.ImportBatchStatus;

public record ImportBatchProgressSnapshot(
        Long batchId,
        String batchName,
        ImportBatchStatus status,
        int processedRows,
        int plannedRunnableRows,
        int completedFiles,
        int runnableFiles,
        double progressValue,
        String percentLabel,
        String remainingLabel,
        String throughputLabel,
        String startedLabel,
        String currentFileName,
        String currentEntityLabel,
        boolean liveEstimate,
        boolean historicalEstimate) {
}
