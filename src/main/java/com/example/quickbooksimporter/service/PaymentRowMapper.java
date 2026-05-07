package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedPayment;
import com.example.quickbooksimporter.domain.NormalizedPaymentField;
import com.example.quickbooksimporter.domain.ParsedCsvRow;
import com.example.quickbooksimporter.domain.PaymentApplication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class PaymentRowMapper {

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    public NormalizedPayment map(ParsedCsvRow row, Map<NormalizedPaymentField, String> mapping) {
        String customer = value(row.values(), mapping.get(NormalizedPaymentField.CUSTOMER));
        LocalDate paymentDate = parseDate(value(row.values(), mapping.get(NormalizedPaymentField.PAYMENT_DATE)));
        String referenceNo = value(row.values(), mapping.get(NormalizedPaymentField.REFERENCE_NO));
        String paymentMethod = value(row.values(), mapping.get(NormalizedPaymentField.PAYMENT_METHOD));
        String depositAccount = value(row.values(), mapping.get(NormalizedPaymentField.DEPOSIT_ACCOUNT));
        String invoiceNo = value(row.values(), mapping.get(NormalizedPaymentField.INVOICE_NO));
        BigDecimal appliedAmount = parseDecimal(value(row.values(), mapping.get(NormalizedPaymentField.APPLIED_AMOUNT)));
        BigDecimal paymentAmount = parseDecimal(value(row.values(), mapping.get(NormalizedPaymentField.PAYMENT_AMOUNT)));
        return new NormalizedPayment(
                customer,
                paymentDate,
                referenceNo,
                paymentMethod,
                depositAccount,
                paymentAmount,
                new PaymentApplication(invoiceNo, appliedAmount));
    }

    private String value(Map<String, String> source, String header) {
        if (StringUtils.isBlank(header)) {
            return null;
        }
        String value = source.get(header);
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("Invalid payment date: " + value);
    }

    private BigDecimal parseDecimal(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid amount: " + value);
        }
    }
}
