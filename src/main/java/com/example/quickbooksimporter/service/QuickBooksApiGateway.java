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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class QuickBooksApiGateway implements QuickBooksGateway {
    private static final Logger log = LoggerFactory.getLogger(QuickBooksApiGateway.class);

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
        InvoiceLine line = invoice.lines().getFirst();
        Map<String, Object> payload = payloadFactory.build(
                invoice,
                findCustomerRef(realmId, invoice.customer()),
                findItemRef(realmId, line.itemName()));
        InvoiceResponse response = post(realmId, "/v3/company/" + realmId + "/invoice", payload, InvoiceResponse.class);
        return new QuickBooksInvoiceCreateResult(response.invoice().id(), response.invoice().docNumber());
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

        PaymentResponse response = post(realmId, "/v3/company/" + realmId + "/payment", payload, PaymentResponse.class);
        return new QuickBooksPaymentCreateResult(response.payment().id(), response.payment().docNumber());
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
        PurchaseResponse response = post(realmId, "/v3/company/" + realmId + "/purchase", payload, PurchaseResponse.class);
        return new QuickBooksExpenseCreateResult(response.purchase().id(), response.purchase().docNumber());
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
        SalesReceiptResponse response = post(realmId, "/v3/company/" + realmId + "/salesreceipt", payload, SalesReceiptResponse.class);
        return new QuickBooksSalesReceiptCreateResult(response.salesReceipt().id(), response.salesReceipt().docNumber());
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
        payload.put("APAccountRef", Map.of("value", apAccountId));
        payload.put("TxnDate", String.valueOf(bill.txnDate()));
        payload.put("DueDate", bill.dueDate() == null ? null : String.valueOf(bill.dueDate()));
        payload.put("DocNumber", bill.billNo());
        payload.put("Line", lines);
        BillResponse response = post(realmId, "/v3/company/" + realmId + "/bill", payload, BillResponse.class);
        return new QuickBooksBillCreateResult(response.bill().id(), response.bill().docNumber());
    }

    @Override
    public QuickBooksPaymentCreateResult createBillPayment(String realmId, NormalizedBillPayment payment, QuickBooksBillRef billRef) {
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
        BillPaymentResponse response = post(realmId, "/v3/company/" + realmId + "/billpayment", payload, BillPaymentResponse.class);
        return new QuickBooksPaymentCreateResult(response.billPayment().id(), response.billPayment().docNumber());
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
                .fromUriString(properties.baseUrl() + "/v3/company/" + realmId + "/query")
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

    private <T> T post(String realmId, String path, Map<String, Object> payload, Class<T> responseType) {
        String token = connectionService.getActiveConnection().getAccessToken();
        return restClient.post()
                .uri(URI.create(properties.baseUrl() + path + "?minorversion=75"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(responseType);
    }

    public record QueryResponse(@JsonAlias("QueryResponse") Map<String, Object> queryResponse) {
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
