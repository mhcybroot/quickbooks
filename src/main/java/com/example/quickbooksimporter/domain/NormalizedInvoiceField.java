package com.example.quickbooksimporter.domain;

public enum NormalizedInvoiceField {
    INVOICE_NO("*InvoiceNo"),
    CUSTOMER("*Customer"),
    INVOICE_DATE("*InvoiceDate"),
    DUE_DATE("*DueDate"),
    TERMS("Terms"),
    LOCATION("Location"),
    MEMO("Memo"),
    ITEM_NAME("Item(Product/Service)"),
    ITEM_DESCRIPTION("ItemDescription"),
    ITEM_QUANTITY("ItemQuantity"),
    ITEM_RATE("ItemRate"),
    ITEM_AMOUNT("*ItemAmount"),
    TAXABLE("Taxable"),
    TAX_RATE("TaxRate"),
    SERVICE_DATE("Service Date");

    private final String sampleHeader;

    NormalizedInvoiceField(String sampleHeader) {
        this.sampleHeader = sampleHeader;
    }

    public String sampleHeader() {
        return sampleHeader;
    }
}
