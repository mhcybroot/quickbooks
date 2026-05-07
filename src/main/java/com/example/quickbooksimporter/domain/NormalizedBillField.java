package com.example.quickbooksimporter.domain;

public enum NormalizedBillField {
    BILL_NO("BillNo"),
    VENDOR("Vendor"),
    TXN_DATE("TxnDate"),
    DUE_DATE("DueDate"),
    AP_ACCOUNT("APAccount"),
    ITEM_NAME("Item"),
    CATEGORY("Category"),
    DESCRIPTION("Description"),
    QUANTITY("Quantity"),
    RATE("Rate"),
    AMOUNT("Amount"),
    TAXABLE("Taxable"),
    TAX_CODE("TaxCode"),
    TAX_RATE("TaxRate");

    private final String sampleHeader;

    NormalizedBillField(String sampleHeader) {
        this.sampleHeader = sampleHeader;
    }

    public String sampleHeader() {
        return sampleHeader;
    }
}
