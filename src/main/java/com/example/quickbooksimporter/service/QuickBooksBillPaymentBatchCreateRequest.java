package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedBillPayment;

public record QuickBooksBillPaymentBatchCreateRequest(
        NormalizedBillPayment payment,
        QuickBooksBillRef billRef) {
}
