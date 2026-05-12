package com.example.quickbooksimporter.service;

public enum ReconciliationDisposition {
    AUTO_MATCHED,
    NEEDS_REVIEW,
    BANK_ONLY,
    QBO_ONLY,
    APPLIED,
    APPLY_FAILED,
    PARTIAL_APPLY_FAILED,
    SKIPPED
}
