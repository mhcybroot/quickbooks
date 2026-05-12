package com.example.quickbooksimporter.service;

public record ReconciliationMatchResult(
        int bankRowNumber,
        ReconciliationTier tier,
        int confidence,
        ReconciliationDisposition disposition,
        String rationale,
        QboReconCandidate candidate,
        java.util.List<QboReconCandidate> candidates,
        String groupKey,
        boolean batch,
        String allocationMode,
        java.time.LocalDate groupWindowStart,
        java.time.LocalDate groupWindowEnd,
        String patternType,
        String patternKey,
        String woKey,
        boolean woMatched,
        String woSource) {

    public int candidateCount() {
        return candidates == null ? 0 : candidates.size();
    }

    public String candidateTxnIds() {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        return candidates.stream().map(QboReconCandidate::txnId).collect(java.util.stream.Collectors.joining("|"));
    }
}
