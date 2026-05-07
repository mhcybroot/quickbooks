package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.net.URI;
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
}
