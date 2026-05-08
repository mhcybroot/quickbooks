package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedPayment;
import com.example.quickbooksimporter.domain.PaymentRowValidationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class PaymentImportValidator {

    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway gateway;

    public PaymentImportValidator(QuickBooksConnectionService connectionService, QuickBooksGateway gateway) {
        this.connectionService = connectionService;
        this.gateway = gateway;
    }

    public PaymentRowValidationResult validate(int rowNumber, Map<String, String> rawData, NormalizedPayment payment) {
        return validate(rowNumber, rawData, payment, BigDecimal.ZERO, null);
    }

    public PaymentRowValidationResult validate(int rowNumber,
                                               Map<String, String> rawData,
                                               NormalizedPayment payment,
                                               BigDecimal alreadyAllocatedInPreview) {
        return validate(rowNumber, rawData, payment, alreadyAllocatedInPreview, null);
    }

    public PaymentRowValidationResult validate(int rowNumber,
                                               Map<String, String> rawData,
                                               NormalizedPayment payment,
                                               BigDecimal alreadyAllocatedInPreview,
                                               QuickBooksInvoiceRef draftInvoiceRef) {
        List<String> errors = new ArrayList<>();
        if (payment == null) {
            errors.add("Payment could not be parsed");
        } else {
            require(payment.customer(), "Customer is required", errors);
            require(payment.referenceNo(), "Reference number is required", errors);
            require(payment.depositAccount(), "Deposit account is required", errors);
            if (payment.paymentDate() == null) {
                errors.add("Payment date is required");
            }
            if (payment.application() == null) {
                errors.add("Invoice application is required");
            } else {
                require(payment.application().invoiceNo(), "Invoice number is required", errors);
                if (payment.application().appliedAmount() == null) {
                    errors.add("Applied amount is required");
                } else if (payment.application().appliedAmount().signum() <= 0) {
                    errors.add("Applied amount must be greater than 0");
                }
            }
            if (payment.paymentAmount() == null) {
                errors.add("Payment amount is required");
            } else if (payment.paymentAmount().signum() <= 0) {
                errors.add("Payment amount must be greater than 0");
            }
            if (payment.paymentAmount() != null && payment.application() != null && payment.application().appliedAmount() != null
                    && payment.paymentAmount().compareTo(payment.application().appliedAmount()) != 0) {
                errors.add("Payment amount must equal applied amount in v1");
            }
        }

        String realmId = connectionService.getConnection().map(connection -> connection.getRealmId()).orElse(null);
        if (errors.isEmpty() && realmId != null) {
            QuickBooksInvoiceRef invoiceRef = draftInvoiceRef != null
                    ? draftInvoiceRef
                    : gateway.findInvoiceByDocNumber(realmId, payment.application().invoiceNo());
            if (invoiceRef == null) {
                errors.add("Invoice not found in QuickBooks");
            } else {
                if (!StringUtils.equalsIgnoreCase(payment.customer(), invoiceRef.customerName())) {
                    errors.add("Invoice customer does not match payment customer");
                }
                BigDecimal openBalance = invoiceRef.openBalance() == null ? BigDecimal.ZERO : invoiceRef.openBalance();
                BigDecimal allocatedInPreview = alreadyAllocatedInPreview == null ? BigDecimal.ZERO : alreadyAllocatedInPreview;
                BigDecimal remainingBalance = openBalance.subtract(allocatedInPreview).max(BigDecimal.ZERO);
                if (remainingBalance.signum() <= 0) {
                    errors.add("Invoice is already fully paid or closed");
                } else if (payment.application().appliedAmount().compareTo(remainingBalance) > 0) {
                    errors.add("Applied amount exceeds invoice open balance");
                }
            }
            if (gateway.paymentExistsByReference(realmId, payment.customer(), payment.paymentDate(), payment.referenceNo())) {
                errors.add("Payment reference already exists in QuickBooks for this customer and date");
            }
        }

        ImportRowStatus status = errors.isEmpty() ? ImportRowStatus.READY : ImportRowStatus.INVALID;
        return new PaymentRowValidationResult(
                rowNumber,
                new com.example.quickbooksimporter.domain.ParsedCsvRow(rowNumber, rawData),
                payment,
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
