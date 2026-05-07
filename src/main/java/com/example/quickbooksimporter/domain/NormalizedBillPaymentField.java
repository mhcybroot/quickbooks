package com.example.quickbooksimporter.domain;

public enum NormalizedBillPaymentField {
    VENDOR("Vendor"),
    PAYMENT_DATE("PaymentDate"),
    REFERENCE_NO("ReferenceNo"),
    PAYMENT_ACCOUNT("PaymentAccount"),
    BILL_NO("BillNo"),
    APPLIED_AMOUNT("AppliedAmount"),
    PAYMENT_AMOUNT("PaymentAmount");

    private final String sampleHeader;

    NormalizedBillPaymentField(String sampleHeader) {
        this.sampleHeader = sampleHeader;
    }

    public String sampleHeader() {
        return sampleHeader;
    }
}
