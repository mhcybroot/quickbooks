package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedPayment;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import java.util.List;
import java.time.LocalDate;

public interface QuickBooksGateway {

    boolean invoiceExists(String realmId, String invoiceNumber);

    boolean serviceItemExists(String realmId, String itemName);

    List<QuickBooksIncomeAccount> listIncomeAccounts(String realmId);

    void ensureCustomer(String realmId, String customerName);

    void ensureServiceItem(String realmId, String itemName, String description);

    QuickBooksInvoiceCreateResult createInvoice(String realmId, NormalizedInvoice invoice);

    QuickBooksInvoiceRef findInvoiceByDocNumber(String realmId, String invoiceNo);

    boolean paymentExistsByReference(String realmId, String customerName, LocalDate paymentDate, String referenceNo);

    QuickBooksPaymentCreateResult createPayment(String realmId, NormalizedPayment payment, QuickBooksInvoiceRef invoiceRef);
}
