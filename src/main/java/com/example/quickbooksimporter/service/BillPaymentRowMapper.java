package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.BillPaymentApplication;
import com.example.quickbooksimporter.domain.NormalizedBillPayment;
import com.example.quickbooksimporter.domain.NormalizedBillPaymentField;
import com.example.quickbooksimporter.domain.ParsedCsvRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class BillPaymentRowMapper {
    public NormalizedBillPayment map(ParsedCsvRow row, Map<NormalizedBillPaymentField, String> mapping) {
        return map(row, mapping, DateFormatOption.AUTO);
    }

    public NormalizedBillPayment map(ParsedCsvRow row,
                                     Map<NormalizedBillPaymentField, String> mapping,
                                     DateFormatOption dateFormatOption) {
        DateFormatOption effective = dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption;
        return new NormalizedBillPayment(
                value(row.values(), mapping.get(NormalizedBillPaymentField.VENDOR)),
                date(value(row.values(), mapping.get(NormalizedBillPaymentField.PAYMENT_DATE)), effective),
                value(row.values(), mapping.get(NormalizedBillPaymentField.REFERENCE_NO)),
                value(row.values(), mapping.get(NormalizedBillPaymentField.PAYMENT_ACCOUNT)),
                decimal(value(row.values(), mapping.get(NormalizedBillPaymentField.PAYMENT_AMOUNT))),
                new BillPaymentApplication(
                        value(row.values(), mapping.get(NormalizedBillPaymentField.BILL_NO)),
                        decimal(value(row.values(), mapping.get(NormalizedBillPaymentField.APPLIED_AMOUNT)))));
    }

    private String value(Map<String, String> values, String header) {
        if (StringUtils.isBlank(header)) return null;
        return StringUtils.trimToNull(values.get(header));
    }
    private LocalDate date(String v, DateFormatOption option) {
        if (v == null) return null;
        return option.parse(v.trim());
    }
    private BigDecimal decimal(String v) {
        if (v == null) return null;
        try { return new BigDecimal(v.replace(",", "").replace("$", "").trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid amount: " + v); }
    }
}
