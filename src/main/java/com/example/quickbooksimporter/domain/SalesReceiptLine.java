package com.example.quickbooksimporter.domain;

import java.math.BigDecimal;

public record SalesReceiptLine(
        String itemName,
        String description,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal amount,
        boolean taxable,
        String taxCode,
        BigDecimal taxRate) {
}
