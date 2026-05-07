package com.example.quickbooksimporter.service;

import java.math.BigDecimal;

public record QuickBooksInvoiceRef(
        String invoiceId,
        String docNumber,
        String customerId,
        String customerName,
        BigDecimal openBalance) {
}
