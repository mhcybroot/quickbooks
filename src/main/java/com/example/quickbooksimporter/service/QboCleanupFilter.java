package com.example.quickbooksimporter.service;

import java.time.LocalDate;
import java.math.BigDecimal;

public record QboCleanupFilter(
        LocalDate fromDate,
        LocalDate toDate,
        String docNumberContains,
        String partyContains,
        String statusContains,
        BigDecimal amountMin,
        BigDecimal amountMax,
        BigDecimal balanceMin,
        BigDecimal balanceMax,
        String idContains,
        QboCleanupSortField sortField,
        QboSortDirection sortDirection,
        int pageSize) {

    public static QboCleanupFilter defaults() {
        return new QboCleanupFilter(
                null, null, null, null, null,
                null, null, null, null, null,
                QboCleanupSortField.TXN_DATE,
                QboSortDirection.DESC,
                200);
    }
}
