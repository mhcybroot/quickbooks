package com.example.quickbooksimporter.domain;

public enum ImportRunStatus {
    DRAFT,
    PREVIEW_READY,
    QUEUED,
    RUNNING,
    VALIDATION_FAILED,
    IMPORTED,
    PARTIAL_FAILURE,
    FAILED
}
