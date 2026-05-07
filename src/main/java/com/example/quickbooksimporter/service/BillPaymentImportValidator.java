package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.BillPaymentRowValidationResult;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedBillPayment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class BillPaymentImportValidator {
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway gateway;

    public BillPaymentImportValidator(QuickBooksConnectionService connectionService, QuickBooksGateway gateway) {
        this.connectionService = connectionService;
        this.gateway = gateway;
    }

    public BillPaymentRowValidationResult validate(int rowNumber, Map<String, String> rawData, NormalizedBillPayment payment) {
        List<String> errors = new ArrayList<>();
        if (payment == null) {
            errors.add("Bill payment could not be parsed");
        } else {
            req(payment.vendor(), "Vendor is required", errors);
            req(payment.referenceNo(), "Reference number is required", errors);
            req(payment.paymentAccount(), "Payment account is required", errors);
            if (payment.paymentDate() == null) errors.add("Payment date is required");
            if (payment.application() == null || StringUtils.isBlank(payment.application().billNo())) errors.add("Bill number is required");
            if (payment.application() == null || payment.application().appliedAmount() == null) errors.add("Applied amount is required");
            else if (payment.application().appliedAmount().signum() <= 0) errors.add("Applied amount must be greater than 0");
            if (payment.paymentAmount() == null) errors.add("Payment amount is required");
            else if (payment.paymentAmount().signum() <= 0) errors.add("Payment amount must be greater than 0");
            if (payment.paymentAmount() != null && payment.application() != null && payment.application().appliedAmount() != null
                    && payment.paymentAmount().compareTo(payment.application().appliedAmount()) != 0) {
                errors.add("Payment amount must equal applied amount in v1");
            }
        }

        String realmId = connectionService.getConnection().map(c -> c.getRealmId()).orElse(null);
        if (errors.isEmpty() && realmId != null) {
            QuickBooksBillRef billRef = gateway.findBillByDocNumber(realmId, payment.application().billNo());
            if (billRef == null) errors.add("Bill not found in QuickBooks");
            else {
                if (!StringUtils.equalsIgnoreCase(payment.vendor(), billRef.vendorName())) errors.add("Bill vendor does not match payment vendor");
                if (billRef.openBalance() == null || billRef.openBalance().signum() <= 0) errors.add("Bill is already fully paid or closed");
                else if (payment.application().appliedAmount().compareTo(billRef.openBalance()) > 0) errors.add("Applied amount exceeds bill open balance");
            }
            if (gateway.findAccountIdByName(realmId, payment.paymentAccount()) == null) errors.add("Payment account not found in QuickBooks");
            if (gateway.billPaymentExists(realmId, payment.vendor(), payment.paymentDate(), payment.referenceNo(), payment.paymentAmount())) {
                errors.add("Bill payment already exists in QuickBooks for vendor/date/reference/amount");
            }
        }

        ImportRowStatus status = errors.isEmpty() ? ImportRowStatus.READY : ImportRowStatus.INVALID;
        return new BillPaymentRowValidationResult(rowNumber, new com.example.quickbooksimporter.domain.ParsedCsvRow(rowNumber, rawData), payment, status, String.join("; ", errors), rawData);
    }

    private void req(String value, String message, List<String> errors) {
        if (StringUtils.isBlank(value)) errors.add(message);
    }
}
