package com.example.quickbooksimporter.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QboTransactionRow(
        String id,
        String syncToken,
        QboCleanupEntityType entityType,
        String externalNumber,
        LocalDate txnDate,
        String partyName,
        BigDecimal totalAmount,
        BigDecimal balance,
        String status) {
}
