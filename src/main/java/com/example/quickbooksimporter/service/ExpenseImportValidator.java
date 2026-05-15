package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.ExpenseRowValidationResult;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedExpense;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ExpenseImportValidator {

    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway gateway;

    public ExpenseImportValidator(QuickBooksConnectionService connectionService, QuickBooksGateway gateway) {
        this.connectionService = connectionService;
        this.gateway = gateway;
    }

    public ExpenseRowValidationResult validate(int rowNumber, Map<String, String> rawData, NormalizedExpense expense, boolean skipQuickBooksChecks) {
        List<String> errors = new ArrayList<>();
        if (expense == null) {
            errors.add("Expense could not be parsed");
        } else {
            require(expense.vendor(), "Vendor is required", errors);
            require(expense.paymentAccount(), "Payment account is required", errors);
            require(expense.category(), "Category is required", errors);
            if (expense.txnDate() == null) {
                errors.add("Transaction date is required");
            }
            if (expense.amount() == null) {
                errors.add("Amount is required");
            } else if (expense.amount().signum() <= 0) {
                errors.add("Amount must be greater than 0");
            }
        }

        String realmId = connectionService.getConnection().map(connection -> connection.getRealmId()).orElse(null);
        if (errors.isEmpty() && realmId != null && !skipQuickBooksChecks) {
            if (gateway.expenseExists(realmId, expense.vendor(), expense.txnDate(), expense.amount(), expense.referenceNo())) {
                errors.add("Expense already exists in QuickBooks for vendor/date/amount/reference");
            }
            if (gateway.findAccountIdByName(realmId, expense.paymentAccount()) == null) {
                errors.add("Payment account not found in QuickBooks");
            }
        }

        ImportRowStatus status = errors.isEmpty() ? ImportRowStatus.READY : ImportRowStatus.INVALID;
        return new ExpenseRowValidationResult(
                rowNumber,
                new com.example.quickbooksimporter.domain.ParsedCsvRow(rowNumber, rawData),
                expense,
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
