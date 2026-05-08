package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.EntityType;

public final class ImportRoutes {

    private ImportRoutes() {
    }

    public static String routeFor(EntityType entityType) {
        return switch (entityType) {
            case INVOICE -> "invoices";
            case PAYMENT -> "payments";
            case EXPENSE -> "expenses";
            case SALES_RECEIPT -> "sales-receipts";
            case BILL -> "bills";
            case BILL_PAYMENT -> "bill-payments";
        };
    }
}
