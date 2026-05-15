package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedSalesReceipt;
import com.example.quickbooksimporter.domain.SalesReceiptLine;
import com.example.quickbooksimporter.domain.SalesReceiptRowValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class SalesReceiptImportValidator {

    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway gateway;

    public SalesReceiptImportValidator(QuickBooksConnectionService connectionService, QuickBooksGateway gateway) {
        this.connectionService = connectionService;
        this.gateway = gateway;
    }

    public SalesReceiptRowValidationResult validate(int rowNumber,
                                                    Map<String, String> rawData,
                                                    NormalizedSalesReceipt receipt,
                                                    boolean skipQuickBooksChecks) {
        List<String> errors = new ArrayList<>();
        if (receipt == null) {
            errors.add("Sales receipt could not be parsed");
        } else {
            require(receipt.receiptNo(), "Receipt number is required", errors);
            require(receipt.customer(), "Customer is required", errors);
            require(receipt.depositAccount(), "Deposit account is required", errors);
            if (receipt.txnDate() == null) {
                errors.add("Transaction date is required");
            }
            if (receipt.lines() == null || receipt.lines().isEmpty()) {
                errors.add("At least one line is required");
            } else {
                for (SalesReceiptLine line : receipt.lines()) {
                    require(line.itemName(), "Item is required", errors);
                    if (line.amount() == null || line.amount().signum() <= 0) {
                        errors.add("Line amount must be greater than 0");
                    }
                    if (line.quantity() == null || line.quantity().signum() <= 0) {
                        errors.add("Line quantity must be greater than 0");
                    }
                    if (line.taxable() && StringUtils.isBlank(line.taxCode())) {
                        errors.add("Tax code is required for taxable lines");
                    }
                }
            }
        }

        String realmId = connectionService.getConnection().map(connection -> connection.getRealmId()).orElse(null);
        if (errors.isEmpty() && realmId != null && !skipQuickBooksChecks) {
            if (!gateway.customerExists(realmId, receipt.customer())) {
                errors.add("Customer not found in QuickBooks");
            }
            if (gateway.findAccountIdByName(realmId, receipt.depositAccount()) == null) {
                errors.add("Deposit account not found in QuickBooks");
            }
            if (gateway.salesReceiptExistsByDocNumber(realmId, receipt.receiptNo())) {
                errors.add("Sales receipt number already exists in QuickBooks");
            }
            for (SalesReceiptLine line : receipt.lines()) {
                if (!gateway.serviceItemExists(realmId, line.itemName())) {
                    errors.add("Item not found in QuickBooks: " + line.itemName());
                }
                if (line.taxable() && !gateway.taxCodeExists(realmId, line.taxCode())) {
                    errors.add("Tax code not found in QuickBooks: " + line.taxCode());
                }
            }
        }

        ImportRowStatus status = errors.isEmpty() ? ImportRowStatus.READY : ImportRowStatus.INVALID;
        return new SalesReceiptRowValidationResult(
                rowNumber,
                new com.example.quickbooksimporter.domain.ParsedCsvRow(rowNumber, rawData),
                receipt,
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
