package com.example.quickbooksimporter.service;

import java.util.List;

public record QboCleanupSearchJobResult(
        List<QboTransactionRow> rows,
        String summaryText) {
}
