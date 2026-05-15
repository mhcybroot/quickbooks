package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.BillLine;
import com.example.quickbooksimporter.domain.BillRowValidationResult;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedBill;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class BillImportValidator {
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway gateway;

    public BillImportValidator(QuickBooksConnectionService connectionService, QuickBooksGateway gateway) {
        this.connectionService = connectionService;
        this.gateway = gateway;
    }

    public BillRowValidationResult validate(int rowNumber, Map<String, String> rawData, NormalizedBill bill, boolean skipQuickBooksChecks) {
        List<String> errors = new ArrayList<>();
        if (bill == null) errors.add("Bill could not be parsed");
        else {
            req(bill.billNo(), "Bill number is required", errors);
            req(bill.vendor(), "Vendor is required", errors);
            req(bill.apAccount(), "AP account is required", errors);
            if (bill.txnDate() == null) errors.add("Transaction date is required");
            if (bill.lines() == null || bill.lines().isEmpty()) errors.add("At least one bill line is required");
            else for (BillLine line : bill.lines()) {
                if (StringUtils.isBlank(line.itemName()) && StringUtils.isBlank(line.category())) {
                    errors.add("Bill line needs item or category");
                }
                if (line.amount() == null || line.amount().signum() <= 0) errors.add("Bill line amount must be greater than 0");
                if (line.taxable() && StringUtils.isBlank(line.taxCode())) errors.add("Tax code is required for taxable lines");
            }
        }

        String realmId = connectionService.getConnection().map(c -> c.getRealmId()).orElse(null);
        if (errors.isEmpty() && realmId != null && !skipQuickBooksChecks) {
            if (gateway.billExistsByDocNumber(realmId, bill.billNo())) errors.add("Bill number already exists in QuickBooks");
            if (gateway.findAccountIdByName(realmId, bill.apAccount()) == null) errors.add("AP account not found in QuickBooks");
            for (BillLine line : bill.lines()) {
                if (!StringUtils.isBlank(line.itemName()) && !gateway.serviceItemExists(realmId, line.itemName())) {
                    errors.add("Item not found in QuickBooks: " + line.itemName());
                }
                if (!StringUtils.isBlank(line.category()) && gateway.findAccountIdByName(realmId, line.category()) == null) {
                    // allowed, will auto-create
                }
                if (line.taxable() && !gateway.taxCodeExists(realmId, line.taxCode())) {
                    errors.add("Tax code not found in QuickBooks: " + line.taxCode());
                }
            }
        }
        ImportRowStatus status = errors.isEmpty() ? ImportRowStatus.READY : ImportRowStatus.INVALID;
        return new BillRowValidationResult(rowNumber, new com.example.quickbooksimporter.domain.ParsedCsvRow(rowNumber, rawData), bill, status, String.join("; ", errors), rawData);
    }

    private void req(String v, String m, List<String> errors) { if (StringUtils.isBlank(v)) errors.add(m); }
}
