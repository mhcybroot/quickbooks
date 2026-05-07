package com.example.quickbooksimporter.domain;

import java.time.LocalDate;
import java.util.List;

public record NormalizedInvoice(
        String invoiceNo,
        String customer,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String terms,
        String location,
        String memo,
        List<InvoiceLine> lines) {
}
