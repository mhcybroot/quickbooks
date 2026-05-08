package com.example.quickbooksimporter.domain;

public enum ImportRowStatus {
    PARSED,
    INVALID,
    READY,
    DUPLICATE,
    SKIPPED,
    IMPORTED,
    FAILED
}
