package com.example.quickbooksimporter.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NormalizedBillPayment(
        String vendor,
        LocalDate paymentDate,
        String referenceNo,
        String paymentAccount,
        BigDecimal paymentAmount,
        BillPaymentApplication application) {
}
