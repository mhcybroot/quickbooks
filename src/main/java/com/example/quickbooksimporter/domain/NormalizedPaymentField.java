package com.example.quickbooksimporter.domain;

public enum NormalizedPaymentField {
    CUSTOMER("Customer"),
    PAYMENT_DATE("PaymentDate"),
    REFERENCE_NO("ReferenceNo"),
    PAYMENT_METHOD("PaymentMethod"),
    DEPOSIT_ACCOUNT("DepositToAccount"),
    INVOICE_NO("InvoiceNo"),
    APPLIED_AMOUNT("AppliedAmount"),
    PAYMENT_AMOUNT("PaymentAmount");

    private final String sampleHeader;

    NormalizedPaymentField(String sampleHeader) {
        this.sampleHeader = sampleHeader;
    }

    public String sampleHeader() {
        return sampleHeader;
    }
}
