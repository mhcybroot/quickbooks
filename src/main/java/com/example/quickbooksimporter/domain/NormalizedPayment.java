package com.example.quickbooksimporter.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NormalizedPayment(
        String customer,
        LocalDate paymentDate,
        String referenceNo,
        String paymentMethod,
        String depositAccount,
        BigDecimal paymentAmount,
        PaymentApplication application) {
}
