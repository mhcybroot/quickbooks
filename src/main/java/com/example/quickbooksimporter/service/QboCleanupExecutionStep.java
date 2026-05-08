package com.example.quickbooksimporter.service;

public record QboCleanupExecutionStep(
        QboTransactionRow transaction,
        String action) {
}
