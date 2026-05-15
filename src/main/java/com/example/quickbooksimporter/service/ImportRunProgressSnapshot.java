package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRunStatus;

public record ImportRunProgressSnapshot(
                Long runId,
                EntityType entityType,
                String sourceFileName,
                ImportRunStatus status,
                int processedRows,
                int runnableRows,
                int skippedRows,
                int importedRows,
                double progressValue,
                String percentLabel,
                String remainingLabel,
                String throughputLabel,
                String startedLabel,
                boolean liveEstimate,
                boolean historicalEstimate) {
}
