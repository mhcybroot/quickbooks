package com.example.quickbooksimporter.service;

public enum QboCleanupEntityType {
    INVOICE("Invoice", "DocNumber", true, true),
    SALES_RECEIPT("SalesReceipt", "DocNumber", true, true),
    BILL("Bill", "DocNumber", true, true),
    BILL_PAYMENT("BillPayment", "DocNumber", true, true),
    RECEIVE_PAYMENT("Payment", "PaymentRefNum", true, true),
    EXPENSE("Purchase", "DocNumber", true, true);

    private final String qboEntityName;
    private final String numberField;
    private final boolean deleteSupported;
    private final boolean voidSupported;

    QboCleanupEntityType(String qboEntityName, String numberField, boolean deleteSupported, boolean voidSupported) {
        this.qboEntityName = qboEntityName;
        this.numberField = numberField;
        this.deleteSupported = deleteSupported;
        this.voidSupported = voidSupported;
    }

    public String qboEntityName() {
        return qboEntityName;
    }

    public String numberField() {
        return numberField;
    }

    public boolean deleteSupported() {
        return deleteSupported;
    }

    public boolean voidSupported() {
        return voidSupported;
    }
}
