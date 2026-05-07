package com.example.quickbooksimporter.service;

import java.math.BigDecimal;

public record QuickBooksBillRef(
        String billId,
        String docNumber,
        String vendorId,
        String vendorName,
        BigDecimal openBalance) {
}
