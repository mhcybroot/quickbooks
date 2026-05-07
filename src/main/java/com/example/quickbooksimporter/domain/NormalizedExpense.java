package com.example.quickbooksimporter.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NormalizedExpense(
        String vendor,
        LocalDate txnDate,
        String referenceNo,
        String paymentAccount,
        String category,
        String description,
        BigDecimal amount) {
}
