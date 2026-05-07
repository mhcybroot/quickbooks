package com.example.quickbooksimporter.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceLine(
        String itemName,
        String description,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal amount,
        boolean taxable,
        BigDecimal taxRate,
        LocalDate serviceDate) {
}
