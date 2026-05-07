package com.example.quickbooksimporter.domain;

import java.math.BigDecimal;

public record BillPaymentApplication(String billNo, BigDecimal appliedAmount) {
}
