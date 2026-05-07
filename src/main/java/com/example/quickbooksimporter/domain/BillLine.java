package com.example.quickbooksimporter.domain;

import java.math.BigDecimal;

public record BillLine(
        String itemName,
        String category,
        String description,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal amount,
        boolean taxable,
        String taxCode,
        BigDecimal taxRate) {
}
