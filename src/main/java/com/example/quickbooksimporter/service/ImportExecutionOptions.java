package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.persistence.ImportBatchEntity;

public record ImportExecutionOptions(
        ImportBatchEntity batch,
        Integer batchOrder,
        String dependencyGroup,
        ImportExecutionMode executionMode) {

    public static ImportExecutionOptions standalone() {
        return new ImportExecutionOptions(null, null, null, ImportExecutionMode.STRICT_ALL_ROWS);
    }
}
