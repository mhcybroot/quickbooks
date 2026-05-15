package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.RowValidationResult;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class InvoiceImportValidator {

    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway gateway;

    public InvoiceImportValidator(QuickBooksConnectionService connectionService, QuickBooksGateway gateway) {
        this.connectionService = connectionService;
        this.gateway = gateway;
    }

    public RowValidationResult validate(int rowNumber, java.util.Map<String, String> rawData, NormalizedInvoice invoice, boolean skipQuickBooksChecks) {
        List<String> errors = new ArrayList<>();
        if (invoice == null) {
            errors.add("Invoice could not be parsed");
        } else {
            require(invoice.invoiceNo(), "Invoice number is required", errors);
            require(invoice.customer(), "Customer is required", errors);
            if (invoice.invoiceDate() == null) {
                errors.add("Invoice date is required");
            }
            if (invoice.dueDate() == null) {
                errors.add("Due date is required");
            }
            if (invoice.lines().isEmpty()) {
                errors.add("At least one invoice line is required");
            }
            for (InvoiceLine line : invoice.lines()) {
                require(line.itemName(), "Item name is required", errors);
                if (line.amount() == null) {
                    errors.add("Item amount is required");
                }
                if (line.taxable() && line.taxRate() == null) {
                    errors.add("Tax rate is required when Taxable is Y");
                }
                if (line.taxable() && line.taxRate() != null && line.taxRate().signum() > 0) {
                    errors.add("Taxable invoice lines with a positive tax rate are not supported for direct import in v1");
                }
            }
        }

        String realmId = connectionService.getConnection()
                .map(connection -> connection.getRealmId())
                .orElse(null);
        if (errors.isEmpty() && realmId != null && !skipQuickBooksChecks) {
            if (gateway.invoiceExists(realmId, invoice.invoiceNo())) {
                errors.add("Invoice number already exists in QuickBooks");
            }
        }

        ImportRowStatus status = errors.isEmpty() ? ImportRowStatus.READY : ImportRowStatus.INVALID;
        return new RowValidationResult(
                rowNumber,
                new com.example.quickbooksimporter.domain.ParsedCsvRow(rowNumber, rawData),
                invoice,
                status,
                String.join("; ", errors),
                rawData);
    }

    private void require(String value, String message, List<String> errors) {
        if (StringUtils.isBlank(value)) {
            errors.add(message);
        }
    }
}
