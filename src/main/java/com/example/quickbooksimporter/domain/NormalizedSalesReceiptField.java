package com.example.quickbooksimporter.domain;

public enum NormalizedSalesReceiptField {
    RECEIPT_NO("ReceiptNo"),
    CUSTOMER("Customer"),
    TXN_DATE("TxnDate"),
    PAYMENT_METHOD("PaymentMethod"),
    DEPOSIT_ACCOUNT("DepositAccount"),
    ITEM_NAME("Item"),
    DESCRIPTION("Description"),
    QUANTITY("Quantity"),
    RATE("Rate"),
    AMOUNT("Amount"),
    TAXABLE("Taxable"),
    TAX_CODE("TaxCode"),
    TAX_RATE("TaxRate");

    private final String sampleHeader;

    NormalizedSalesReceiptField(String sampleHeader) {
        this.sampleHeader = sampleHeader;
    }

    public String sampleHeader() {
        return sampleHeader;
    }
}
