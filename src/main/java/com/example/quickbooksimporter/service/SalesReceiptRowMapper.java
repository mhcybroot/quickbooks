package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedSalesReceiptField;
import com.example.quickbooksimporter.domain.ParsedCsvRow;
import com.example.quickbooksimporter.domain.SalesReceiptLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class SalesReceiptRowMapper {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE);

    public SalesReceiptRowMapped map(ParsedCsvRow row, Map<NormalizedSalesReceiptField, String> mapping) {
        BigDecimal quantity = parseDecimal(value(row.values(), mapping.get(NormalizedSalesReceiptField.QUANTITY)));
        BigDecimal rate = parseDecimal(value(row.values(), mapping.get(NormalizedSalesReceiptField.RATE)));
        BigDecimal amount = parseDecimal(value(row.values(), mapping.get(NormalizedSalesReceiptField.AMOUNT)));
        if (amount == null && quantity != null && rate != null) {
            amount = quantity.multiply(rate);
        }
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        boolean taxable = "Y".equalsIgnoreCase(value(row.values(), mapping.get(NormalizedSalesReceiptField.TAXABLE)))
                || "true".equalsIgnoreCase(value(row.values(), mapping.get(NormalizedSalesReceiptField.TAXABLE)));
        SalesReceiptLine line = new SalesReceiptLine(
                value(row.values(), mapping.get(NormalizedSalesReceiptField.ITEM_NAME)),
                value(row.values(), mapping.get(NormalizedSalesReceiptField.DESCRIPTION)),
                quantity,
                rate,
                amount,
                taxable,
                value(row.values(), mapping.get(NormalizedSalesReceiptField.TAX_CODE)),
                parseDecimal(value(row.values(), mapping.get(NormalizedSalesReceiptField.TAX_RATE))));
        return new SalesReceiptRowMapped(
                row.rowNumber(),
                row.values(),
                value(row.values(), mapping.get(NormalizedSalesReceiptField.RECEIPT_NO)),
                value(row.values(), mapping.get(NormalizedSalesReceiptField.CUSTOMER)),
                parseDate(value(row.values(), mapping.get(NormalizedSalesReceiptField.TXN_DATE))),
                value(row.values(), mapping.get(NormalizedSalesReceiptField.PAYMENT_METHOD)),
                value(row.values(), mapping.get(NormalizedSalesReceiptField.DEPOSIT_ACCOUNT)),
                line);
    }

    private String value(Map<String, String> values, String header) {
        if (StringUtils.isBlank(header)) {
            return null;
        }
        return StringUtils.trimToNull(values.get(header));
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("Invalid date: " + value);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").replace("$", "").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number: " + value);
        }
    }

    public record SalesReceiptRowMapped(
            int rowNumber,
            Map<String, String> rawData,
            String receiptNo,
            String customer,
            LocalDate txnDate,
            String paymentMethod,
            String depositAccount,
            SalesReceiptLine line) {
    }
}
