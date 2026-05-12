package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedExpense;
import com.example.quickbooksimporter.domain.NormalizedExpenseField;
import com.example.quickbooksimporter.domain.ParsedCsvRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ExpenseRowMapper {

    public NormalizedExpense map(ParsedCsvRow row, Map<NormalizedExpenseField, String> mapping) {
        return map(row, mapping, DateFormatOption.AUTO);
    }

    public NormalizedExpense map(ParsedCsvRow row,
                                 Map<NormalizedExpenseField, String> mapping,
                                 DateFormatOption dateFormatOption) {
        DateFormatOption effective = dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption;
        return new NormalizedExpense(
                value(row.values(), mapping.get(NormalizedExpenseField.VENDOR)),
                parseDate(value(row.values(), mapping.get(NormalizedExpenseField.TXN_DATE)), effective),
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

    private LocalDate parseDate(String value, DateFormatOption option) {
        if (value == null) {
            return null;
        }
        return option.parse(value.trim());
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
