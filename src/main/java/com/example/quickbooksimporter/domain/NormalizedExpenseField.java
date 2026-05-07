package com.example.quickbooksimporter.domain;

public enum NormalizedExpenseField {
    VENDOR("Vendor"),
    TXN_DATE("TxnDate"),
    REFERENCE_NO("ReferenceNo"),
    PAYMENT_ACCOUNT("PaymentAccount"),
    CATEGORY("Category"),
    DESCRIPTION("Description"),
    AMOUNT("Amount");

    private final String sampleHeader;

    NormalizedExpenseField(String sampleHeader) {
        this.sampleHeader = sampleHeader;
    }

    public String sampleHeader() {
        return sampleHeader;
    }
}
