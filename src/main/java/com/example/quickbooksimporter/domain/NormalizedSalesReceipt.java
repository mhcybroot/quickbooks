package com.example.quickbooksimporter.domain;

import java.time.LocalDate;
import java.util.List;

public record NormalizedSalesReceipt(
        String receiptNo,
        String customer,
        LocalDate txnDate,
        String paymentMethod,
        String depositAccount,
        List<SalesReceiptLine> lines) {
}
