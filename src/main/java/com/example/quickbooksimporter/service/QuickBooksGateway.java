package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedInvoice;
import java.util.List;

public interface QuickBooksGateway {

    boolean invoiceExists(String realmId, String invoiceNumber);

    boolean serviceItemExists(String realmId, String itemName);

    List<QuickBooksIncomeAccount> listIncomeAccounts(String realmId);

    void ensureCustomer(String realmId, String customerName);

    void ensureServiceItem(String realmId, String itemName, String description);

    QuickBooksInvoiceCreateResult createInvoice(String realmId, NormalizedInvoice invoice);
}
