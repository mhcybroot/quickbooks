package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedPayment;

public record QuickBooksPaymentBatchCreateRequest(
        NormalizedPayment payment,
        QuickBooksInvoiceRef invoiceRef) {
}
