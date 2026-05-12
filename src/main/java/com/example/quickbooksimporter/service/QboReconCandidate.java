package com.example.quickbooksimporter.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QboReconCandidate(
        String txnId,
        String syncToken,
        String entityType,
        LocalDate txnDate,
        BigDecimal signedAmount,
        String reference,
        String party,
        String privateNote) {
}
