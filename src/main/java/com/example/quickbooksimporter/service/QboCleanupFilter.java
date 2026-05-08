package com.example.quickbooksimporter.service;

import java.time.LocalDate;

public record QboCleanupFilter(
        LocalDate fromDate,
        LocalDate toDate,
        String docNumberContains,
        String partyContains,
        int pageSize) {

    public static QboCleanupFilter defaults() {
        return new QboCleanupFilter(null, null, null, null, 200);
    }
}
