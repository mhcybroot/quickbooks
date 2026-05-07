package com.example.quickbooksimporter.domain;

import java.time.LocalDate;
import java.util.List;

public record NormalizedBill(
        String billNo,
        String vendor,
        LocalDate txnDate,
        LocalDate dueDate,
        String apAccount,
        List<BillLine> lines) {
}
