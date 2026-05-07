package com.example.quickbooksimporter.domain;

import java.math.BigDecimal;

public record PaymentApplication(String invoiceNo, BigDecimal appliedAmount) {
}
