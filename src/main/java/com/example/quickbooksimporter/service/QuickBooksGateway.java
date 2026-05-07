package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedPayment;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.NormalizedExpense;
import com.example.quickbooksimporter.domain.NormalizedSalesReceipt;
import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;

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

    void ensureVendor(String realmId, String vendorName);

    void ensureExpenseCategory(String realmId, String categoryName);

    String findAccountIdByName(String realmId, String accountName);

    boolean expenseExists(String realmId, String vendorName, LocalDate txnDate, BigDecimal amount, String referenceNo);

    QuickBooksExpenseCreateResult createExpense(String realmId, NormalizedExpense expense);

    boolean customerExists(String realmId, String customerName);

    boolean taxCodeExists(String realmId, String taxCodeName);

    boolean salesReceiptExistsByDocNumber(String realmId, String docNumber);

    void ensurePaymentMethod(String realmId, String paymentMethodName);

    QuickBooksSalesReceiptCreateResult createSalesReceipt(String realmId, NormalizedSalesReceipt receipt);
}
