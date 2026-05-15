package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.BillLine;
import com.example.quickbooksimporter.domain.NormalizedExpense;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.NormalizedPayment;
import com.example.quickbooksimporter.domain.NormalizedSalesReceipt;
import com.example.quickbooksimporter.domain.NormalizedBill;
import com.example.quickbooksimporter.domain.NormalizedBillPayment;
import com.example.quickbooksimporter.domain.SalesReceiptLine;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class QuickBooksApiGateway implements QuickBooksGateway {
    private static final Logger log = LoggerFactory.getLogger(QuickBooksApiGateway.class);
    static final int QBO_BATCH_MAX_ITEMS = 10;

    private final QuickBooksProperties properties;
    private final QuickBooksConnectionService connectionService;
    private final RestClient restClient;
    private final QuickBooksInvoicePayloadFactory payloadFactory;

    public QuickBooksApiGateway(QuickBooksProperties properties,
                                QuickBooksConnectionService connectionService,
                                RestClient restClient,
                                QuickBooksInvoicePayloadFactory payloadFactory) {
        this.properties = properties;
        this.connectionService = connectionService;
        this.restClient = restClient;
        this.payloadFactory = payloadFactory;
    }

    @Override
    public boolean invoiceExists(String realmId, String invoiceNumber) {
        if (invoiceNumber == null) {
            return false;
        }
        String query = "select Id from Invoice where DocNumber = '" + qbLiteral(invoiceNumber) + "'";
        QueryResponse response = query(realmId, query);
        return response.queryResponse() != null && response.queryResponse().containsKey("Invoice");
    }

    @Override
    public void ensureCustomer(String realmId, String customerName) {
        if (customerName == null) {
            return;
        }
        String query = "select Id from Customer where DisplayName = '" + qbLiteral(customerName) + "'";
        QueryResponse response = query(realmId, query);
        if (response.queryResponse() != null && response.queryResponse().containsKey("Customer")) {
            return;
        }
        post(realmId, "/v3/company/" + realmId + "/customer",
                Map.of("DisplayName", customerName));
    }

    @Override
    public boolean customerExists(String realmId, String customerName) {
        if (customerName == null) {
            return false;
        }
        return findCustomerId(realmId, customerName) != null;
    }

    @Override
    public void ensureVendor(String realmId, String vendorName) {
        if (vendorName == null) {
            return;
        }
        String query = "select Id from Vendor where DisplayName = '" + qbLiteral(vendorName) + "'";
        QueryResponse response = query(realmId, query);
        if (response.queryResponse() != null && response.queryResponse().containsKey("Vendor")) {
            return;
        }
        post(realmId, "/v3/company/" + realmId + "/vendor",
                Map.of("DisplayName", vendorName));
    }

    @Override
    public void ensureExpenseCategory(String realmId, String categoryName) {
        if (categoryName == null) {
            return;
        }
        String query = "select Id from Account where Name = '" + qbLiteral(categoryName) + "'";
        QueryResponse response = query(realmId, query);
        if (response.queryResponse() != null && response.queryResponse().containsKey("Account")) {
            return;
        }
        post(realmId, "/v3/company/" + realmId + "/account",
                Map.of(
                        "Name", categoryName,
                        "AccountType", "Expense"));
    }

    @Override
    public boolean taxCodeExists(String realmId, String taxCodeName) {
        if (taxCodeName == null) {
            return false;
        }
        String query = "select Id from TaxCode where Name = '" + qbLiteral(taxCodeName) + "'";
        QueryResponse response = query(realmId, query);
        return response.queryResponse() != null && response.queryResponse().containsKey("TaxCode");
    }

    @Override
    public boolean serviceItemExists(String realmId, String itemName) {
        if (itemName == null) {
            return false;
        }
        String query = "select Id from Item where Name = '" + qbLiteral(itemName) + "'";
        QueryResponse response = query(realmId, query);
        return response.queryResponse() != null && response.queryResponse().containsKey("Item");
    }

    @Override
    public List<QuickBooksIncomeAccount> listIncomeAccounts(String realmId) {
        QueryResponse response = query(realmId, "select Id, Name, AccountSubType from Account where AccountType = 'Income'");
        List<Map<String, Object>> accounts = castList(response.queryResponse(), "Account");
        return accounts.stream()
                .map(account -> new QuickBooksIncomeAccount(
                        String.valueOf(account.get("Id")),
                        String.valueOf(account.get("Name")),
                        String.valueOf(account.getOrDefault("AccountSubType", ""))))
                .sorted(Comparator.comparing(QuickBooksIncomeAccount::name))
                .toList();
    }

    @Override
    public void ensureServiceItem(String realmId, String itemName, String description) {
        if (itemName == null) {
            return;
        }
        String query = "select Id from Item where Name = '" + qbLiteral(itemName) + "'";
        QueryResponse response = query(realmId, query);
        if (response.queryResponse() != null && response.queryResponse().containsKey("Item")) {
            return;
        }
        if (StringUtils.isBlank(properties.serviceItemIncomeAccountId())) {
            throw new IllegalStateException("QB_SERVICE_ITEM_INCOME_ACCOUNT_ID must be configured before auto-creating service items");
        }
        post(realmId, "/v3/company/" + realmId + "/item",
                Map.of(
                        "Name", itemName,
                        "Type", "Service",
                        "IncomeAccountRef", Map.of("value", properties.serviceItemIncomeAccountId()),
                        "Description", description == null ? "" : description));
    }

    @Override
    public QuickBooksInvoiceCreateResult createInvoice(String realmId, NormalizedInvoice invoice) {
        Map<String, Object> payload = buildInvoicePayload(realmId, invoice);
        InvoiceResponse response = post(realmId, "/v3/company/" + realmId + "/invoice", payload, InvoiceResponse.class);
        return new QuickBooksInvoiceCreateResult(response.invoice().id(), response.invoice().docNumber());
    }

    @Override
    public List<QuickBooksBatchCreateResult> createInvoicesBatch(String realmId, List<NormalizedInvoice> invoices) {
        return executeBatchCreate(realmId, "Invoice", invoices, invoice -> buildInvoicePayload(realmId, invoice));
    }

    @Override
    public QuickBooksInvoiceRef findInvoiceByDocNumber(String realmId, String invoiceNo) {
        QueryResponse response = query(realmId, "select Id, DocNumber, Balance, CustomerRef from Invoice where DocNumber = '" + qbLiteral(invoiceNo) + "'");
        List<Map<String, Object>> invoices = castList(response.queryResponse(), "Invoice");
        if (invoices.isEmpty()) {
            return null;
        }
        Map<String, Object> invoice = invoices.getFirst();
        Map<String, Object> customerRef = castMap(invoice.get("CustomerRef"));
        return new QuickBooksInvoiceRef(
                String.valueOf(invoice.get("Id")),
                String.valueOf(invoice.get("DocNumber")),
                customerRef == null ? null : String.valueOf(customerRef.get("value")),
                customerRef == null ? null : String.valueOf(customerRef.get("name")),
                toBigDecimal(invoice.get("Balance")));
    }

    @Override
    public boolean paymentExistsByReference(String realmId, String customerName, LocalDate paymentDate, String referenceNo) {
        if (customerName == null || paymentDate == null || referenceNo == null) {
            return false;
        }
        String customerId = findCustomerId(realmId, customerName);
        if (customerId == null) {
            return false;
        }
        String query = "select Id from Payment where PaymentRefNum = '" + qbLiteral(referenceNo) + "'"
                + " and CustomerRef = '" + qbLiteral(customerId) + "'"
                + " and TxnDate = '" + paymentDate + "'";
        QueryResponse response = query(realmId, query);
        return response.queryResponse() != null && response.queryResponse().containsKey("Payment");
    }

    @Override
    public QuickBooksPaymentCreateResult createPayment(String realmId, NormalizedPayment payment, QuickBooksInvoiceRef invoiceRef) {
        java.util.Map<String, Object> payload = buildPaymentPayload(realmId, payment, invoiceRef);
        PaymentResponse response = post(realmId, "/v3/company/" + realmId + "/payment", payload, PaymentResponse.class);
        return new QuickBooksPaymentCreateResult(response.payment().id(), response.payment().docNumber());
    }

    @Override
    public List<QuickBooksBatchCreateResult> createPaymentsBatch(String realmId, List<QuickBooksPaymentBatchCreateRequest> payments) {
        return executeBatchCreate(realmId, "Payment", payments, request -> buildPaymentPayload(realmId, request.payment(), request.invoiceRef()));
    }

    @Override
    public boolean expenseExists(String realmId, String vendorName, LocalDate txnDate, BigDecimal amount, String referenceNo) {
        if (txnDate == null || amount == null || referenceNo == null) {
            return false;
        }
        String query = "select Id from Purchase where TxnDate = '" + txnDate + "'"
                + " and TotalAmt = '" + amount + "'"
                + " and DocNumber = '" + qbLiteral(referenceNo) + "'";
        QueryResponse response = query(realmId, query);
        return response.queryResponse() != null && response.queryResponse().containsKey("Purchase");
    }

    @Override
    public QuickBooksExpenseCreateResult createExpense(String realmId, NormalizedExpense expense) {
        java.util.Map<String, Object> payload = buildExpensePayload(realmId, expense);
        PurchaseResponse response = post(realmId, "/v3/company/" + realmId + "/purchase", payload, PurchaseResponse.class);
        return new QuickBooksExpenseCreateResult(response.purchase().id(), response.purchase().docNumber());
    }

    @Override
    public List<QuickBooksBatchCreateResult> createExpensesBatch(String realmId, List<NormalizedExpense> expenses) {
        return executeBatchCreate(realmId, "Purchase", expenses, expense -> buildExpensePayload(realmId, expense));
    }

    @Override
    public boolean salesReceiptExistsByDocNumber(String realmId, String docNumber) {
        if (docNumber == null) {
            return false;
        }
        QueryResponse response = query(realmId, "select Id from SalesReceipt where DocNumber = '" + qbLiteral(docNumber) + "'");
        return response.queryResponse() != null && response.queryResponse().containsKey("SalesReceipt");
    }

    @Override
    public void ensurePaymentMethod(String realmId, String paymentMethodName) {
        if (StringUtils.isBlank(paymentMethodName)) {
            return;
        }
        String existing = findPaymentMethodIdByName(realmId, paymentMethodName);
        if (existing != null) {
            return;
        }
        post(realmId, "/v3/company/" + realmId + "/paymentmethod",
                Map.of("Name", paymentMethodName, "Type", "NON_CREDIT_CARD"));
    }

    @Override
    public QuickBooksSalesReceiptCreateResult createSalesReceipt(String realmId, NormalizedSalesReceipt receipt) {
        java.util.Map<String, Object> payload = buildSalesReceiptPayload(realmId, receipt);
        SalesReceiptResponse response = post(realmId, "/v3/company/" + realmId + "/salesreceipt", payload, SalesReceiptResponse.class);
        return new QuickBooksSalesReceiptCreateResult(response.salesReceipt().id(), response.salesReceipt().docNumber());
    }

    @Override
    public List<QuickBooksBatchCreateResult> createSalesReceiptsBatch(String realmId, List<NormalizedSalesReceipt> receipts) {
        return executeBatchCreate(realmId, "SalesReceipt", receipts, receipt -> buildSalesReceiptPayload(realmId, receipt));
    }

    @Override
    public boolean billExistsByDocNumber(String realmId, String docNumber) {
        if (docNumber == null) {
            return false;
        }
        QueryResponse response = query(realmId, "select Id from Bill where DocNumber = '" + qbLiteral(docNumber) + "'");
        return response.queryResponse() != null && response.queryResponse().containsKey("Bill");
    }

    @Override
    public QuickBooksBillRef findBillByDocNumber(String realmId, String billNo) {
        QueryResponse response = query(realmId, "select Id, DocNumber, Balance, VendorRef from Bill where DocNumber = '" + qbLiteral(billNo) + "'");
        List<Map<String, Object>> bills = castList(response.queryResponse(), "Bill");
        if (bills.isEmpty()) {
            return null;
        }
        Map<String, Object> bill = bills.getFirst();
        Map<String, Object> vendorRef = castMap(bill.get("VendorRef"));
        return new QuickBooksBillRef(
                String.valueOf(bill.get("Id")),
                String.valueOf(bill.get("DocNumber")),
                vendorRef == null ? null : String.valueOf(vendorRef.get("value")),
                vendorRef == null ? null : String.valueOf(vendorRef.get("name")),
                toBigDecimal(bill.get("Balance")));
    }

    @Override
    public boolean billPaymentExists(String realmId, String vendorName, LocalDate paymentDate, String referenceNo, BigDecimal amount) {
        if (vendorName == null || paymentDate == null || referenceNo == null || amount == null) {
            return false;
        }
        String vendorId = findVendorId(realmId, vendorName);
        if (vendorId == null) {
            return false;
        }
        String query = "select Id from BillPayment where VendorRef = '" + qbLiteral(vendorId) + "'"
                + " and TxnDate = '" + paymentDate + "'"
                + " and TotalAmt = '" + amount + "'"
                + " and DocNumber = '" + qbLiteral(referenceNo) + "'";
        QueryResponse response = query(realmId, query);
        return response.queryResponse() != null && response.queryResponse().containsKey("BillPayment");
    }

    @Override
    public QuickBooksBillCreateResult createBill(String realmId, NormalizedBill bill) {
        java.util.Map<String, Object> payload = buildBillPayload(realmId, bill);
        BillResponse response = post(realmId, "/v3/company/" + realmId + "/bill", payload, BillResponse.class);
        return new QuickBooksBillCreateResult(response.bill().id(), response.bill().docNumber());
    }

    @Override
    public List<QuickBooksBatchCreateResult> createBillsBatch(String realmId, List<NormalizedBill> bills) {
        return executeBatchCreate(realmId, "Bill", bills, bill -> buildBillBatchPayload(realmId, bill));
    }

    @Override
    public QuickBooksPaymentCreateResult createBillPayment(String realmId, NormalizedBillPayment payment, QuickBooksBillRef billRef) {
        java.util.Map<String, Object> payload = buildBillPaymentPayload(realmId, payment, billRef);
        BillPaymentResponse response = post(realmId, "/v3/company/" + realmId + "/billpayment", payload, BillPaymentResponse.class);
        return new QuickBooksPaymentCreateResult(response.billPayment().id(), response.billPayment().docNumber());
    }

    @Override
    public List<QuickBooksBatchCreateResult> createBillPaymentsBatch(String realmId, List<QuickBooksBillPaymentBatchCreateRequest> payments) {
        return executeBatchCreate(realmId, "BillPayment", payments, request -> buildBillPaymentPayload(realmId, request.payment(), request.billRef()));
    }

    @Override
    public List<QboTransactionRow> listTransactions(String realmId,
                                                    QboCleanupEntityType type,
                                                    QboCleanupFilter filter,
                                                    Integer startPosition) {
        int page = filter == null || filter.pageSize() <= 0 ? 200 : filter.pageSize();
        int offset = startPosition == null || startPosition < 1 ? 1 : startPosition;
        String query = buildCleanupListQuery(type, filter, offset, page);
        QueryResponse response = query(realmId, query);
        List<Map<String, Object>> rows = castList(response.queryResponse(), type.qboEntityName());
        return rows.stream().map(row -> toCleanupRow(type, row)).toList();
    }

    Map<String, Object> buildInvoicePayload(String realmId, NormalizedInvoice invoice) {
        return payloadFactory.build(
                invoice,
                findCustomerRef(realmId, invoice.customer()),
                line -> findItemRef(realmId, line.itemName()));
    }

    Map<String, Object> buildPaymentPayload(String realmId, NormalizedPayment payment, QuickBooksInvoiceRef invoiceRef) {
        String customerId = invoiceRef.customerId() != null ? invoiceRef.customerId() : findCustomerId(realmId, payment.customer());
        if (customerId == null) {
            throw new IllegalStateException("Customer not found in QuickBooks: " + payment.customer());
        }
        String depositAccountId = findAccountIdByName(realmId, payment.depositAccount());
        if (depositAccountId == null) {
            throw new IllegalStateException("Deposit account not found in QuickBooks: " + payment.depositAccount());
        }

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("CustomerRef", Map.of("value", customerId));
        payload.put("TxnDate", String.valueOf(payment.paymentDate()));
        payload.put("TotalAmt", payment.paymentAmount());
        payload.put("PaymentRefNum", payment.referenceNo());
        payload.put("DepositToAccountRef", Map.of("value", depositAccountId));
        if (!StringUtils.isBlank(payment.paymentMethod())) {
            String methodId = findPaymentMethodIdByName(realmId, payment.paymentMethod());
            if (methodId == null) {
                log.warn("Payment method '{}' not found in QuickBooks. Continuing without PaymentMethodRef.", payment.paymentMethod());
            } else {
                payload.put("PaymentMethodRef", Map.of("value", methodId));
            }
        }
        payload.put("Line", List.of(Map.of(
                "Amount", payment.application().appliedAmount(),
                "LinkedTxn", List.of(Map.of(
                        "TxnId", invoiceRef.invoiceId(),
                        "TxnType", "Invoice")))));
        return payload;
    }

    Map<String, Object> buildExpensePayload(String realmId, NormalizedExpense expense) {
        String vendorId = findVendorId(realmId, expense.vendor());
        if (vendorId == null) {
            throw new IllegalStateException("Vendor not found in QuickBooks: " + expense.vendor());
        }
        String paymentAccountId = findAccountIdByName(realmId, expense.paymentAccount());
        if (paymentAccountId == null) {
            throw new IllegalStateException("Payment account not found in QuickBooks: " + expense.paymentAccount());
        }
        String categoryAccountId = findAccountIdByName(realmId, expense.category());
        if (categoryAccountId == null) {
            throw new IllegalStateException("Expense category account not found in QuickBooks: " + expense.category());
        }

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("TxnDate", String.valueOf(expense.txnDate()));
        payload.put("DocNumber", expense.referenceNo());
        payload.put("TotalAmt", expense.amount());
        payload.put("PaymentType", "Cash");
        payload.put("EntityRef", Map.of("value", vendorId, "type", "Vendor"));
        payload.put("AccountRef", Map.of("value", paymentAccountId));
        payload.put("Line", List.of(Map.of(
                "Amount", expense.amount(),
                "Description", expense.description() == null ? "" : expense.description(),
                "DetailType", "AccountBasedExpenseLineDetail",
                "AccountBasedExpenseLineDetail", Map.of("AccountRef", Map.of("value", categoryAccountId)))));
        return payload;
    }

    Map<String, Object> buildSalesReceiptPayload(String realmId, NormalizedSalesReceipt receipt) {
        String customerId = findCustomerId(realmId, receipt.customer());
        if (customerId == null) {
            throw new IllegalStateException("Customer not found in QuickBooks: " + receipt.customer());
        }
        String depositAccountId = findAccountIdByName(realmId, receipt.depositAccount());
        if (depositAccountId == null) {
            throw new IllegalStateException("Deposit account not found in QuickBooks: " + receipt.depositAccount());
        }

        java.util.List<java.util.Map<String, Object>> lines = new java.util.ArrayList<>();
        for (SalesReceiptLine line : receipt.lines()) {
            String itemId = findItemId(realmId, line.itemName());
            if (itemId == null) {
                throw new IllegalStateException("Item not found in QuickBooks: " + line.itemName());
            }
            java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("ItemRef", Map.of("value", itemId));
            detail.put("Qty", line.quantity());
            if (line.rate() != null) {
                detail.put("UnitPrice", line.rate());
            } else if (line.amount() != null && line.quantity() != null && line.quantity().signum() != 0) {
                detail.put("UnitPrice", line.amount().divide(line.quantity(), 6, java.math.RoundingMode.HALF_UP));
            }
            if (line.taxable() && !StringUtils.isBlank(line.taxCode())) {
                String taxCodeId = findTaxCodeIdByName(realmId, line.taxCode());
                if (taxCodeId == null) {
                    throw new IllegalStateException("Tax code not found in QuickBooks: " + line.taxCode());
                }
                detail.put("TaxCodeRef", Map.of("value", taxCodeId));
            }
            lines.add(Map.of(
                    "Amount", line.amount(),
                    "Description", line.description() == null ? "" : line.description(),
                    "DetailType", "SalesItemLineDetail",
                    "SalesItemLineDetail", detail));
        }

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("DocNumber", receipt.receiptNo());
        payload.put("TxnDate", String.valueOf(receipt.txnDate()));
        payload.put("CustomerRef", Map.of("value", customerId));
        payload.put("DepositToAccountRef", Map.of("value", depositAccountId));
        payload.put("Line", lines);
        if (!StringUtils.isBlank(receipt.paymentMethod())) {
            String methodId = findPaymentMethodIdByName(realmId, receipt.paymentMethod());
            if (methodId != null) {
                payload.put("PaymentMethodRef", Map.of("value", methodId));
            }
        }
        return payload;
    }

    Map<String, Object> buildBillPayload(String realmId, NormalizedBill bill) {
        String vendorId = findVendorId(realmId, bill.vendor());
        if (vendorId == null) {
            throw new IllegalStateException("Vendor not found in QuickBooks: " + bill.vendor());
        }
        String apAccountId = findAccountIdByName(realmId, bill.apAccount());
        if (apAccountId == null) {
            throw new IllegalStateException("AP account not found in QuickBooks: " + bill.apAccount());
        }
        java.util.List<java.util.Map<String, Object>> lines = new java.util.ArrayList<>();
        for (BillLine line : bill.lines()) {
            java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
            if (!StringUtils.isBlank(line.itemName())) {
                String itemId = findItemId(realmId, line.itemName());
                if (itemId == null) {
                    throw new IllegalStateException("Item not found in QuickBooks: " + line.itemName());
                }
                detail.put("ItemRef", Map.of("value", itemId));
                detail.put("Qty", line.quantity());
                if (line.rate() != null) {
                    detail.put("UnitPrice", line.rate());
                }
                lines.add(Map.of(
                        "Amount", line.amount(),
                        "Description", line.description() == null ? "" : line.description(),
                        "DetailType", "ItemBasedExpenseLineDetail",
                        "ItemBasedExpenseLineDetail", detail));
            } else {
                String categoryId = findAccountIdByName(realmId, line.category());
                if (categoryId == null) {
                    throw new IllegalStateException("Category not found in QuickBooks: " + line.category());
                }
                detail.put("AccountRef", Map.of("value", categoryId));
                lines.add(Map.of(
                        "Amount", line.amount(),
                        "Description", line.description() == null ? "" : line.description(),
                        "DetailType", "AccountBasedExpenseLineDetail",
                        "AccountBasedExpenseLineDetail", detail));
            }
        }

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("VendorRef", Map.of("value", vendorId));
        payload.put("TxnDate", String.valueOf(bill.txnDate()));
        if (bill.dueDate() != null) {
            payload.put("DueDate", String.valueOf(bill.dueDate()));
        }
        payload.put("DocNumber", bill.billNo());
        payload.put("Line", lines);
        return payload;
    }

    /**
     * Builds a Bill payload for batch create operations.
     * Note: QBO Batch API does not support APAccountRef for Bill create.
     * The AP account is determined from the Vendor's default settings.
     */
    Map<String, Object> buildBillBatchPayload(String realmId, NormalizedBill bill) {
        String vendorId = findVendorId(realmId, bill.vendor());
        if (vendorId == null) {
            throw new IllegalStateException("Vendor not found in QuickBooks: " + bill.vendor());
        }
        java.util.List<java.util.Map<String, Object>> lines = new java.util.ArrayList<>();
        for (BillLine line : bill.lines()) {
            java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
            if (!StringUtils.isBlank(line.itemName())) {
                String itemId = findItemId(realmId, line.itemName());
                if (itemId == null) {
                    throw new IllegalStateException("Item not found in QuickBooks: " + line.itemName());
                }
                detail.put("ItemRef", Map.of("value", itemId));
                detail.put("Qty", line.quantity());
                if (line.rate() != null) {
                    detail.put("UnitPrice", line.rate());
                }
                lines.add(Map.of(
                        "Amount", line.amount(),
                        "Description", line.description() == null ? "" : line.description(),
                        "DetailType", "ItemBasedExpenseLineDetail",
                        "ItemBasedExpenseLineDetail", detail));
            } else {
                String categoryId = findAccountIdByName(realmId, line.category());
                if (categoryId == null) {
                    throw new IllegalStateException("Category not found in QuickBooks: " + line.category());
                }
                detail.put("AccountRef", Map.of("value", categoryId));
                lines.add(Map.of(
                        "Amount", line.amount(),
                        "Description", line.description() == null ? "" : line.description(),
                        "DetailType", "AccountBasedExpenseLineDetail",
                        "AccountBasedExpenseLineDetail", detail));
            }
        }

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("VendorRef", Map.of("value", vendorId));
        // NOTE: APAccountRef is NOT included for batch create - QBO Batch API rejects it
        payload.put("TxnDate", String.valueOf(bill.txnDate()));
        if (bill.dueDate() != null) {
            payload.put("DueDate", String.valueOf(bill.dueDate()));
        }
        payload.put("DocNumber", bill.billNo());
        payload.put("Line", lines);
        return payload;
    }

    Map<String, Object> buildBillPaymentPayload(String realmId, NormalizedBillPayment payment, QuickBooksBillRef billRef) {
        String vendorId = billRef.vendorId() != null ? billRef.vendorId() : findVendorId(realmId, payment.vendor());
        if (vendorId == null) {
            throw new IllegalStateException("Vendor not found in QuickBooks: " + payment.vendor());
        }
        String paymentAccountId = findAccountIdByName(realmId, payment.paymentAccount());
        if (paymentAccountId == null) {
            throw new IllegalStateException("Payment account not found in QuickBooks: " + payment.paymentAccount());
        }
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("VendorRef", Map.of("value", vendorId));
        payload.put("TxnDate", String.valueOf(payment.paymentDate()));
        payload.put("DocNumber", payment.referenceNo());
        payload.put("TotalAmt", payment.paymentAmount());
        payload.put("PayType", "Check");
        payload.put("CheckPayment", Map.of("BankAccountRef", Map.of("value", paymentAccountId)));
        payload.put("Line", List.of(Map.of(
                "Amount", payment.application().appliedAmount(),
                "LinkedTxn", List.of(Map.of("TxnId", billRef.billId(), "TxnType", "Bill")))));
        return payload;
    }

    <T> List<QuickBooksBatchCreateResult> executeBatchCreate(String realmId,
                                                             String entityName,
                                                             List<T> requests,
                                                             Function<T, Map<String, Object>> payloadBuilder) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> payloads = requests.stream().map(payloadBuilder).toList();
        if (log.isDebugEnabled()) {
            log.debug("Batch create payloads for realmId={}, entityType={}, count={}: {}",
                    realmId, entityName, payloads.size(), payloads);
        }
        List<List<BatchItemRequest>> chunks = buildCreateBatchChunks(entityName, payloads);
        List<QuickBooksBatchCreateResult> results = new ArrayList<>(requests.size());
        int offset = 0;
        for (List<BatchItemRequest> chunk : chunks) {
            log.info("QuickBooks batch create start: realmId={}, entityType={}, chunkSize={}", realmId, entityName, chunk.size());
            try {
                BatchResponseEnvelope envelope = postBatch(realmId, chunk);
                List<QuickBooksBatchCreateResult> chunkResults = mapBatchCreateResults(entityName, chunk, envelope.response(), envelope.intuitTid());
                chunkResults.stream()
                        .filter(result -> !result.success())
                        .forEach(result -> log.warn("QuickBooks batch item failed: realmId={}, entityType={}, chunkSize={}, intuit_tid={}, message={}",
                                realmId, entityName, chunk.size(), result.intuitTid(), result.message()));
                results.addAll(chunkResults);
            } catch (RestClientResponseException ex) {
                String intuitTid = extractIntuitTid(ex);
                log.error("QuickBooks batch create failed: realmId={}, entityType={}, chunkSize={}, intuit_tid={}, status={}, body={}",
                        realmId, entityName, chunk.size(), intuitTid, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
                for (int index = 0; index < chunk.size(); index++) {
                    results.add(new QuickBooksBatchCreateResult(false, null, null, extractQboMessage(ex), intuitTid));
                }
            }
            offset += chunk.size();
        }
        return results;
    }

    List<List<BatchItemRequest>> buildCreateBatchChunks(String entityName, List<Map<String, Object>> payloads) {
        List<BatchItemRequest> items = new ArrayList<>(payloads.size());
        for (int index = 0; index < payloads.size(); index++) {
            items.add(new BatchItemRequest(String.valueOf(index + 1), "create", Map.of(entityName, payloads.get(index))));
        }
        List<List<BatchItemRequest>> chunks = new ArrayList<>();
        for (int start = 0; start < items.size(); start += QBO_BATCH_MAX_ITEMS) {
            chunks.add(List.copyOf(items.subList(start, Math.min(start + QBO_BATCH_MAX_ITEMS, items.size()))));
        }
        return chunks;
    }

    List<QuickBooksBatchCreateResult> mapBatchCreateResults(String entityName,
                                                            List<BatchItemRequest> chunk,
                                                            BatchResponse response,
                                                            String intuitTid) {
        Map<String, Map<String, Object>> byBatchId = new HashMap<>();
        if (response != null && response.batchItemResponse() != null) {
            for (Map<String, Object> item : response.batchItemResponse()) {
                byBatchId.put(String.valueOf(item.get("bId")), item);
            }
        }
        List<QuickBooksBatchCreateResult> results = new ArrayList<>(chunk.size());
        for (BatchItemRequest request : chunk) {
            Map<String, Object> item = byBatchId.get(request.bId());
            results.add(toBatchCreateResult(entityName, item, intuitTid));
        }
        return results;
    }

    private BatchResponseEnvelope postBatch(String realmId, List<BatchItemRequest> items) {
        String token = connectionService.getActiveConnection().getAccessToken();
        URI uri = URI.create(connectionService.resolveBaseUrlForCurrentCompany() + "/v3/company/" + realmId + "/batch?minorversion=75");
        ResponseEntity<BatchResponse> entity = restClient.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("BatchItemRequest", items))
                .retrieve()
                .toEntity(BatchResponse.class);
        String intuitTid = entity.getHeaders().getFirst("intuit_tid");
        return new BatchResponseEnvelope(entity.getBody(), intuitTid);
    }

    private QuickBooksBatchCreateResult toBatchCreateResult(String entityName, Map<String, Object> item, String intuitTid) {
        if (item == null) {
            return new QuickBooksBatchCreateResult(false, null, null, "QuickBooks batch response missing item result", intuitTid);
        }
        Map<String, Object> payload = castMap(item.get(entityName));
        if (payload != null) {
            return new QuickBooksBatchCreateResult(
                    true,
                    valueOrNull(payload.get("Id")),
                    valueOrNull(payload.get("DocNumber")),
                    null,
                    intuitTid);
        }
        Map<String, Object> fault = castMap(item.get("Fault"));
        return new QuickBooksBatchCreateResult(false, null, null, extractBatchFaultMessage(fault), intuitTid);
    }

    private String extractBatchFaultMessage(Map<String, Object> fault) {
        if (fault == null) {
            return "QuickBooks batch item failed";
        }
        List<Map<String, Object>> errors = castList(fault, "Error");
        if (!errors.isEmpty()) {
            Map<String, Object> error = errors.getFirst();
            String message = valueOrNull(error.get("Message"));
            String detail = valueOrNull(error.get("Detail"));
            if (StringUtils.isBlank(message)) {
                return detail == null ? "QuickBooks batch item failed" : detail;
            }
            if (StringUtils.isBlank(detail) || detail.equals(message)) {
                return message;
            }
            return message + ": " + detail;
        }
        return valueOrNull(fault.get("type")) == null ? "QuickBooks batch item failed" : String.valueOf(fault.get("type"));
    }

    String buildCleanupListQuery(QboCleanupEntityType type, QboCleanupFilter filter, int startPosition, int maxResults) {
        QboCleanupSortField sortField = filter == null || filter.sortField() == null
                ? QboCleanupSortField.TXN_DATE
                : filter.sortField();
        QboSortDirection sortDirection = filter == null || filter.sortDirection() == null
                ? QboSortDirection.DESC
                : filter.sortDirection();
        return "select " + cleanupSelectFields(type)
                + " from " + type.qboEntityName()
                + buildWhereClause(type, filter)
                + " order by " + toQboOrderField(type, sortField) + " " + sortDirection.name().toLowerCase()
                + ", Id desc"
                + " startposition " + startPosition
                + " maxresults " + maxResults;
    }

    @Override
    public QboCleanupResult deleteTransaction(String realmId, QboCleanupEntityType type, QboTransactionRow transaction) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("Id", transaction.id());
            payload.put("SyncToken", transaction.syncToken());
            payload.put("sparse", true);
            postWithOperation(realmId, "/" + type.qboEntityName().toLowerCase(), payload, "delete", Map.class);
            return new QboCleanupResult(transaction.id(), transaction.externalNumber(), "PARENT", null, "DELETE", true, "Deleted successfully", null);
        } catch (RestClientResponseException ex) {
            log.error("QBO cleanup delete failed: realmId={}, type={}, id={}, syncToken={}, status={}, intuit_tid={}, body={}",
                    realmId, type, transaction.id(), transaction.syncToken(), ex.getStatusCode(), extractIntuitTid(ex), ex.getResponseBodyAsString());
            return new QboCleanupResult(transaction.id(), transaction.externalNumber(), "PARENT", null, "DELETE", false, extractQboMessage(ex), extractIntuitTid(ex));
        }
    }

    @Override
    public QboCleanupResult voidTransaction(String realmId, QboCleanupEntityType type, QboTransactionRow transaction) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("Id", transaction.id());
            payload.put("SyncToken", transaction.syncToken());
            payload.put("sparse", true);
            postWithOperation(realmId, "/" + type.qboEntityName().toLowerCase(), payload, "void", Map.class);
            return new QboCleanupResult(transaction.id(), transaction.externalNumber(), "PARENT", null, "VOID", true, "Voided successfully", null);
        } catch (RestClientResponseException ex) {
            log.error("QBO cleanup void failed: realmId={}, type={}, id={}, syncToken={}, status={}, intuit_tid={}, body={}",
                    realmId, type, transaction.id(), transaction.syncToken(), ex.getStatusCode(), extractIntuitTid(ex), ex.getResponseBodyAsString());
            return new QboCleanupResult(transaction.id(), transaction.externalNumber(), "PARENT", null, "VOID", false, extractQboMessage(ex), extractIntuitTid(ex));
        }
    }

    @Override
    public Map<String, List<QboDependencyBlocker>> findDependencyBlockers(String realmId,
                                                                          QboCleanupEntityType type,
                                                                          List<QboTransactionRow> transactions) {
        Map<String, List<QboDependencyBlocker>> blockers = new HashMap<>();
        for (QboTransactionRow transaction : transactions) {
            List<QboDependencyLink> links = findDirectDependencyLinks(realmId, type, transaction);
            if (!links.isEmpty()) {
                blockers.put(transaction.id(), links.stream()
                        .map(link -> new QboDependencyBlocker(
                                transaction.id(),
                                transaction.externalNumber(),
                                "Linked " + link.childType().qboEntityName() + "(s) exist. Remove dependent records first."))
                        .toList());
            }
        }
        return blockers;
    }

    @Override
    public QboTransactionRow findTransactionById(String realmId, QboCleanupEntityType type, String id) {
        String query = "select " + cleanupSelectFields(type) + " from " + type.qboEntityName()
                + " where Id = '" + qbLiteral(id) + "' startposition 1 maxresults 1";
        QueryResponse response = query(realmId, query);
        List<Map<String, Object>> rows = castList(response.queryResponse(), type.qboEntityName());
        if (rows.isEmpty()) {
            return null;
        }
        return toCleanupRow(type, rows.getFirst());
    }

    @Override
    public List<QboDependencyLink> findDirectDependencyLinks(String realmId, QboCleanupEntityType type, QboTransactionRow transaction) {
        if (transaction == null || StringUtils.isBlank(transaction.id())) {
            return List.of();
        }
        if (type == QboCleanupEntityType.INVOICE) {
            QueryResponse invoiceResponse = query(realmId,
                    "select Id, CustomerRef from Invoice where Id = '" + qbLiteral(transaction.id()) + "' startposition 1 maxresults 1");
            List<Map<String, Object>> invoices = castList(invoiceResponse.queryResponse(), "Invoice");
            if (invoices.isEmpty()) {
                return List.of();
            }
            Map<String, Object> customerRef = castMap(invoices.getFirst().get("CustomerRef"));
            if (customerRef == null || customerRef.get("value") == null) {
                return List.of();
            }
            QueryResponse response = query(realmId,
                    "select Id, Line from Payment where CustomerRef = '" + qbLiteral(String.valueOf(customerRef.get("value"))) + "' startposition 1 maxresults 1000");
            List<Map<String, Object>> rows = castList(response.queryResponse(), "Payment");
            return rows.stream()
                    .filter(row -> hasLinkedTxnId(row, transaction.id()))
                    .map(row -> new QboDependencyLink(transaction.id(), type, String.valueOf(row.get("Id")), QboCleanupEntityType.RECEIVE_PAYMENT, "Linked payment"))
                    .toList();
        }
        if (type == QboCleanupEntityType.BILL) {
            QueryResponse billResponse = query(realmId,
                    "select Id, VendorRef from Bill where Id = '" + qbLiteral(transaction.id()) + "' startposition 1 maxresults 1");
            List<Map<String, Object>> bills = castList(billResponse.queryResponse(), "Bill");
            if (bills.isEmpty()) {
                return List.of();
            }
            Map<String, Object> vendorRef = castMap(bills.getFirst().get("VendorRef"));
            if (vendorRef == null || vendorRef.get("value") == null) {
                return List.of();
            }
            QueryResponse response = query(realmId,
                    "select Id, Line from BillPayment where VendorRef = '" + qbLiteral(String.valueOf(vendorRef.get("value"))) + "' startposition 1 maxresults 1000");
            List<Map<String, Object>> rows = castList(response.queryResponse(), "BillPayment");
            return rows.stream()
                    .filter(row -> hasLinkedTxnId(row, transaction.id()))
                    .map(row -> new QboDependencyLink(transaction.id(), type, String.valueOf(row.get("Id")), QboCleanupEntityType.BILL_PAYMENT, "Linked bill payment"))
                    .toList();
        }
        return List.of();
    }

    @Override
    public List<QboReconCandidate> listReconciliationCandidates(String realmId, LocalDate fromDate, LocalDate toDate) {
        LocalDate from = fromDate == null ? LocalDate.now().minusDays(31) : fromDate;
        LocalDate to = toDate == null ? LocalDate.now() : toDate;
        List<QboReconCandidate> all = new java.util.ArrayList<>();
        all.addAll(fetchReconCandidates(realmId, "Payment",
                "Id, SyncToken, TxnDate, TotalAmt, PaymentRefNum, CustomerRef, PrivateNote",
                "PaymentRefNum", "CustomerRef", false, from, to));
        all.addAll(fetchReconCandidates(realmId, "SalesReceipt",
                "Id, SyncToken, TxnDate, TotalAmt, DocNumber, CustomerRef, PrivateNote",
                "DocNumber", "CustomerRef", false, from, to));
        all.addAll(fetchReconCandidates(realmId, "Purchase",
                "Id, SyncToken, TxnDate, TotalAmt, DocNumber, EntityRef, PrivateNote",
                "DocNumber", "EntityRef", true, from, to));
        all.addAll(fetchReconCandidates(realmId, "BillPayment",
                "Id, SyncToken, TxnDate, TotalAmt, DocNumber, VendorRef, PrivateNote",
                "DocNumber", "VendorRef", true, from, to));
        all.sort(Comparator.comparing(QboReconCandidate::txnDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(QboReconCandidate::txnId));
        return all;
    }

    @Override
    public QuickBooksReconcileMarkResult markTransactionReconciled(String realmId, QboReconCandidate candidate, String note) {
        if (candidate == null || StringUtils.isBlank(candidate.txnId()) || StringUtils.isBlank(candidate.syncToken())) {
            return new QuickBooksReconcileMarkResult(false, "Missing candidate transaction identity/sync token", null);
        }
        String endpoint = "/" + candidate.entityType().toLowerCase();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("Id", candidate.txnId());
            payload.put("SyncToken", candidate.syncToken());
            payload.put("sparse", true);
            payload.put("PrivateNote", note);
            post(realmId, "/v3/company/" + realmId + endpoint, payload, Map.class);
            return new QuickBooksReconcileMarkResult(true, "Marked reconciled", null);
        } catch (RestClientResponseException ex) {
            return new QuickBooksReconcileMarkResult(false, extractQboMessage(ex), extractIntuitTid(ex));
        }
    }

    private Map<String, Object> findCustomerRef(String realmId, String customerName) {
        QueryResponse response = query(realmId, "select Id from Customer where DisplayName = '" + qbLiteral(customerName) + "'");
        List<Map<String, Object>> customers = castList(response.queryResponse(), "Customer");
        if (customers.isEmpty()) {
            throw new IllegalStateException("Customer was not found after auto-create");
        }
        return Map.of("value", String.valueOf(customers.getFirst().get("Id")));
    }

    private Map<String, Object> findItemRef(String realmId, String itemName) {
        QueryResponse response = query(realmId, "select Id from Item where Name = '" + qbLiteral(itemName) + "'");
        List<Map<String, Object>> items = castList(response.queryResponse(), "Item");
        if (items.isEmpty()) {
            throw new IllegalStateException("Item was not found after auto-create");
        }
        return Map.of("value", String.valueOf(items.getFirst().get("Id")));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Map<String, Object> container, String key) {
        if (container == null || !container.containsKey(key)) {
            return List.of();
        }
        return (List<Map<String, Object>>) container.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value == null) {
            return null;
        }
        return (Map<String, Object>) value;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private QueryResponse query(String realmId, String query) {
        String token = connectionService.getActiveConnection().getAccessToken();
        URI uri = UriComponentsBuilder
                .fromUriString(connectionService.resolveBaseUrlForCurrentCompany() + "/v3/company/" + realmId + "/query")
                .queryParam("minorversion", 75)
                .queryParam("query", query)
                .encode()
                .build()
                .toUri();
        try {
            log.debug("QuickBooks query for realm {}: {}", realmId, query);
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(QueryResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("QuickBooks query failed for realm {}. query='{}'. status={} body={}",
                    realmId, query, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        }
    }

    private String qbLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String buildWhereClause(QboCleanupEntityType type, QboCleanupFilter filter) {
        if (filter == null) {
            return "";
        }
        java.util.List<String> conditions = new java.util.ArrayList<>();
        if (filter.fromDate() != null) {
            conditions.add("TxnDate >= '" + filter.fromDate() + "'");
        }
        if (filter.toDate() != null) {
            conditions.add("TxnDate <= '" + filter.toDate() + "'");
        }
        if (!StringUtils.isBlank(filter.docNumberContains())) {
            conditions.add(type.numberField() + " LIKE '%" + qbLiteral(filter.docNumberContains()) + "%'");
        }
        if (!StringUtils.isBlank(filter.statusContains())) {
            conditions.add("PrivateNote LIKE '%" + qbLiteral(filter.statusContains()) + "%'");
        }
        if (filter.amountMin() != null) {
            conditions.add("TotalAmt >= '" + filter.amountMin() + "'");
        }
        if (filter.amountMax() != null) {
            conditions.add("TotalAmt <= '" + filter.amountMax() + "'");
        }
        if (filter.balanceMin() != null && balanceField(type) != null) {
            String balanceField = balanceField(type);
            conditions.add(balanceField + " >= '" + filter.balanceMin() + "'");
        }
        if (filter.balanceMax() != null && balanceField(type) != null) {
            String balanceField = balanceField(type);
            conditions.add(balanceField + " <= '" + filter.balanceMax() + "'");
        }
        if (!StringUtils.isBlank(filter.idContains())) {
            conditions.add("Id LIKE '%" + qbLiteral(filter.idContains()) + "%'");
        }
        if (conditions.isEmpty()) {
            return "";
        }
        return " where " + String.join(" and ", conditions);
    }

    private String toQboOrderField(QboCleanupEntityType type, QboCleanupSortField sortField) {
        return switch (sortField) {
            case TXN_DATE -> "TxnDate";
            case DOC_NUMBER -> type.numberField();
            case PARTY_NAME -> "TxnDate";
            case TOTAL_AMOUNT -> "TotalAmt";
            case BALANCE -> balanceField(type) == null ? "TxnDate" : balanceField(type);
            case QBO_ID -> "Id";
        };
    }

    private String balanceField(QboCleanupEntityType type) {
        return switch (type) {
            case INVOICE, SALES_RECEIPT, BILL -> "Balance";
            // QBO exposes UnappliedAmt on Payment but does not allow querying/sorting by it.
            case RECEIVE_PAYMENT, BILL_PAYMENT, EXPENSE -> null;
        };
    }

    private QboTransactionRow toCleanupRow(QboCleanupEntityType type, Map<String, Object> row) {
        Map<String, Object> customer = castMap(row.get("CustomerRef"));
        Map<String, Object> vendor = castMap(row.get("VendorRef"));
        Map<String, Object> entity = castMap(row.get("EntityRef"));
        String externalNumber = type == QboCleanupEntityType.RECEIVE_PAYMENT
                ? valueOrEmpty(row.get("PaymentRefNum"))
                : valueOrEmpty(row.get("DocNumber"));
        String partyName = customer != null
                ? valueOrEmpty(customer.get("name"))
                : (vendor != null
                        ? valueOrEmpty(vendor.get("name"))
                        : valueOrEmpty(entity == null ? null : entity.get("name")));
        BigDecimal balance = type == QboCleanupEntityType.RECEIVE_PAYMENT
                ? toBigDecimal(row.get("UnappliedAmt"))
                : toBigDecimal(row.get("Balance"));
        return new QboTransactionRow(
                valueOrEmpty(row.get("Id")),
                valueOrEmpty(row.get("SyncToken")),
                type,
                externalNumber,
                parseDate(row.get("TxnDate")),
                partyName,
                toBigDecimal(row.get("TotalAmt")),
                balance,
                valueOrEmpty(row.get("PrivateNote")));
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String valueOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private boolean hasLinkedTxnId(Map<String, Object> row, String txnId) {
        Object linesObject = row.get("Line");
        if (!(linesObject instanceof List<?> lines)) {
            return false;
        }
        for (Object lineObject : lines) {
            if (!(lineObject instanceof Map<?, ?> lineMapRaw)) {
                continue;
            }
            Map<String, Object> lineMap = (Map<String, Object>) lineMapRaw;
            Object linkedTxnObject = lineMap.get("LinkedTxn");
            if (!(linkedTxnObject instanceof List<?> linkedTxns)) {
                continue;
            }
            for (Object linkedTxnEntry : linkedTxns) {
                if (!(linkedTxnEntry instanceof Map<?, ?> linkedTxnMapRaw)) {
                    continue;
                }
                Map<String, Object> linkedTxnMap = (Map<String, Object>) linkedTxnMapRaw;
                if (txnId.equals(String.valueOf(linkedTxnMap.get("TxnId")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String cleanupSelectFields(QboCleanupEntityType type) {
        return switch (type) {
            case INVOICE -> "Id, SyncToken, TxnDate, TotalAmt, Balance, DocNumber, CustomerRef, PrivateNote";
            case SALES_RECEIPT -> "Id, SyncToken, TxnDate, TotalAmt, Balance, DocNumber, CustomerRef, PrivateNote";
            case BILL -> "Id, SyncToken, TxnDate, TotalAmt, Balance, DocNumber, VendorRef, PrivateNote";
            case BILL_PAYMENT -> "Id, SyncToken, TxnDate, TotalAmt, DocNumber, VendorRef, PrivateNote";
            case RECEIVE_PAYMENT -> "Id, SyncToken, TxnDate, TotalAmt, UnappliedAmt, PaymentRefNum, CustomerRef, PrivateNote";
            case EXPENSE -> "Id, SyncToken, TxnDate, TotalAmt, DocNumber, EntityRef, PrivateNote";
        };
    }

    private String extractQboMessage(RestClientResponseException ex) {
        if (StringUtils.isBlank(ex.getResponseBodyAsString())) {
            return ex.getStatusCode() + " " + ex.getStatusText();
        }
        return ex.getStatusCode() + ": " + ex.getResponseBodyAsString();
    }

    private String extractIntuitTid(RestClientResponseException ex) {
        return ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("intuit_tid");
    }

    private String findCustomerId(String realmId, String customerName) {
        QueryResponse response = query(realmId, "select Id from Customer where DisplayName = '" + qbLiteral(customerName) + "'");
        List<Map<String, Object>> customers = castList(response.queryResponse(), "Customer");
        return customers.isEmpty() ? null : String.valueOf(customers.getFirst().get("Id"));
    }

    @Override
    public String findAccountIdByName(String realmId, String accountName) {
        QueryResponse response = query(realmId, "select Id from Account where Name = '" + qbLiteral(accountName) + "'");
        List<Map<String, Object>> accounts = castList(response.queryResponse(), "Account");
        return accounts.isEmpty() ? null : String.valueOf(accounts.getFirst().get("Id"));
    }

    private String findVendorId(String realmId, String vendorName) {
        QueryResponse response = query(realmId, "select Id from Vendor where DisplayName = '" + qbLiteral(vendorName) + "'");
        List<Map<String, Object>> vendors = castList(response.queryResponse(), "Vendor");
        return vendors.isEmpty() ? null : String.valueOf(vendors.getFirst().get("Id"));
    }

    private String findPaymentMethodIdByName(String realmId, String name) {
        QueryResponse response = query(realmId, "select Id from PaymentMethod where Name = '" + qbLiteral(name) + "'");
        List<Map<String, Object>> methods = castList(response.queryResponse(), "PaymentMethod");
        return methods.isEmpty() ? null : String.valueOf(methods.getFirst().get("Id"));
    }

    private String findItemId(String realmId, String itemName) {
        QueryResponse response = query(realmId, "select Id from Item where Name = '" + qbLiteral(itemName) + "'");
        List<Map<String, Object>> items = castList(response.queryResponse(), "Item");
        return items.isEmpty() ? null : String.valueOf(items.getFirst().get("Id"));
    }

    private String findTaxCodeIdByName(String realmId, String name) {
        QueryResponse response = query(realmId, "select Id from TaxCode where Name = '" + qbLiteral(name) + "'");
        List<Map<String, Object>> codes = castList(response.queryResponse(), "TaxCode");
        return codes.isEmpty() ? null : String.valueOf(codes.getFirst().get("Id"));
    }

    private void post(String realmId, String path, Map<String, Object> payload) {
        post(realmId, path, payload, Object.class);
    }

    private List<QboReconCandidate> fetchReconCandidates(String realmId,
                                                         String entityName,
                                                         String selectFields,
                                                         String refField,
                                                         String partyRefField,
                                                         boolean outflow,
                                                         LocalDate from,
                                                         LocalDate to) {
        String query = "select " + selectFields + " from " + entityName
                + " where TxnDate >= '" + from + "' and TxnDate <= '" + to + "'"
                + " order by TxnDate desc startposition 1 maxresults 1000";
        QueryResponse response = query(realmId, query);
        List<Map<String, Object>> rows = castList(response.queryResponse(), entityName);
        return rows.stream().map(row -> {
            Map<String, Object> partyRef = castMap(row.get(partyRefField));
            BigDecimal amount = toBigDecimal(row.get("TotalAmt"));
            if (outflow) {
                amount = amount.negate();
            }
            return new QboReconCandidate(
                    valueOrEmpty(row.get("Id")),
                    valueOrEmpty(row.get("SyncToken")),
                    entityName,
                    parseDate(row.get("TxnDate")),
                    amount,
                    valueOrEmpty(row.get(refField)),
                    valueOrEmpty(partyRef == null ? null : partyRef.get("name")),
                    valueOrEmpty(row.get("PrivateNote")));
        }).toList();
    }

    private <T> T post(String realmId, String path, Map<String, Object> payload, Class<T> responseType) {
        String token = connectionService.getActiveConnection().getAccessToken();
        return restClient.post()
                .uri(URI.create(connectionService.resolveBaseUrlForCurrentCompany() + path + "?minorversion=75"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(responseType);
    }

    private <T> T postWithOperation(String realmId, String path, Map<String, Object> payload, String operation, Class<T> responseType) {
        String token = connectionService.getActiveConnection().getAccessToken();
        String uri = connectionService.resolveBaseUrlForCurrentCompany() + "/v3/company/" + realmId + path + "?operation=" + operation + "&minorversion=75";
        return restClient.post()
                .uri(URI.create(uri))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(responseType);
    }

    public record QueryResponse(@JsonAlias("QueryResponse") Map<String, Object> queryResponse) {
    }

    public record BatchResponse(@JsonAlias("BatchItemResponse") List<Map<String, Object>> batchItemResponse) {
    }

    record BatchResponseEnvelope(BatchResponse response, String intuitTid) {
    }

    record BatchItemRequest(String bId,
                            String operation,
                            Map<String, Object> payload) {
        @com.fasterxml.jackson.annotation.JsonValue
        Map<String, Object> json() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("bId", bId);
            values.put("operation", operation);
            values.putAll(payload);
            return values;
        }
    }

    public record InvoiceResponse(@JsonAlias("Invoice") InvoicePayload invoice) {
    }

    public record InvoicePayload(@JsonAlias("Id") String id, @JsonAlias("DocNumber") String docNumber) {
    }

    public record PaymentResponse(@JsonAlias("Payment") PaymentPayload payment) {
    }

    public record PaymentPayload(@JsonAlias("Id") String id, @JsonAlias("DocNumber") String docNumber) {
    }

    public record PurchaseResponse(@JsonAlias("Purchase") PurchasePayload purchase) {
    }

    public record PurchasePayload(@JsonAlias("Id") String id, @JsonAlias("DocNumber") String docNumber) {
    }

    public record SalesReceiptResponse(@JsonAlias("SalesReceipt") SalesReceiptPayload salesReceipt) {
    }

    public record SalesReceiptPayload(@JsonAlias("Id") String id, @JsonAlias("DocNumber") String docNumber) {
    }

    public record BillResponse(@JsonAlias("Bill") BillPayload bill) {
    }

    public record BillPayload(@JsonAlias("Id") String id, @JsonAlias("DocNumber") String docNumber) {
    }

    public record BillPaymentResponse(@JsonAlias("BillPayment") BillPaymentPayload billPayment) {
    }

    public record BillPaymentPayload(@JsonAlias("Id") String id, @JsonAlias("DocNumber") String docNumber) {
    }
}
