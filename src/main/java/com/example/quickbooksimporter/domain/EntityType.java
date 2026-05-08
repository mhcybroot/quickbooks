package com.example.quickbooksimporter.domain;

public enum EntityType {
    INVOICE,
    PAYMENT,
    EXPENSE,
    SALES_RECEIPT,
    BILL,
    BILL_PAYMENT;

    public String displayName() {
        return switch (this) {
            case INVOICE -> "Invoice";
            case PAYMENT -> "Receive Payment";
            case EXPENSE -> "Expense";
            case SALES_RECEIPT -> "Sales Receipt";
            case BILL -> "Bill";
            case BILL_PAYMENT -> "Bill Payment";
        };
    }

    public int batchPriority() {
        return switch (this) {
            case INVOICE, BILL -> 10;
            case SALES_RECEIPT, EXPENSE -> 20;
            case PAYMENT, BILL_PAYMENT -> 30;
        };
    }

    public EntityType dependencyParent() {
        return switch (this) {
            case PAYMENT -> INVOICE;
            case BILL_PAYMENT -> BILL;
            default -> null;
        };
    }
}
