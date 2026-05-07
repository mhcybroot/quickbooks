package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedExpense;
import com.example.quickbooksimporter.domain.NormalizedExpenseField;
import com.example.quickbooksimporter.domain.ParsedCsvRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ExpenseRowMapper {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE);

    public NormalizedExpense map(ParsedCsvRow row, Map<NormalizedExpenseField, String> mapping) {
        return new NormalizedExpense(
                value(row.values(), mapping.get(NormalizedExpenseField.VENDOR)),
                parseDate(value(row.values(), mapping.get(NormalizedExpenseField.TXN_DATE))),
                value(row.values(), mapping.get(NormalizedExpenseField.REFERENCE_NO)),
                value(row.values(), mapping.get(NormalizedExpenseField.PAYMENT_ACCOUNT)),
                value(row.values(), mapping.get(NormalizedExpenseField.CATEGORY)),
                value(row.values(), mapping.get(NormalizedExpenseField.DESCRIPTION)),
                parseDecimal(value(row.values(), mapping.get(NormalizedExpenseField.AMOUNT))));
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
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid amount: " + value);
        }
    }
}
