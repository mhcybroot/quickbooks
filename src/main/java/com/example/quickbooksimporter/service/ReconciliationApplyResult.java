package com.example.quickbooksimporter.service;

import java.util.List;

public record ReconciliationApplyResult(
        boolean success,
        String message,
        List<ReconciliationMatchResult> appliedRows) {
}
