package com.example.quickbooksimporter.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankStatementRow(
        int rowNumber,
        LocalDate txnDate,
        BigDecimal signedAmount,
        String reference,
        String memo,
        String counterparty) {
}
