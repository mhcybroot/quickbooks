package com.example.quickbooksimporter.service;

import java.util.List;

public record QboCleanupRecoveryResult(
        List<QboCleanupResult> results,
        boolean usedVoidFallback) {
}
