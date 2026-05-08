package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.persistence.ImportBatchEntity;

public record ImportExecutionOptions(
        ImportBatchEntity batch,
        Integer batchOrder,
        String dependencyGroup) {

    public static ImportExecutionOptions standalone() {
        return new ImportExecutionOptions(null, null, null);
    }
}
