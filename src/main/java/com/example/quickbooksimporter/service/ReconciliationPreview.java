package com.example.quickbooksimporter.service;

import java.util.List;

public record ReconciliationPreview(
        Long sessionId,
        List<ReconciliationMatchResult> autoMatched,
        List<ReconciliationMatchResult> needsReview,
        List<ReconciliationMatchResult> bankOnly,
        List<QboReconCandidate> qboOnly) {
}
