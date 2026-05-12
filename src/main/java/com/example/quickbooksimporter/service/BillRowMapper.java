package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.BillLine;
import com.example.quickbooksimporter.domain.NormalizedBillField;
import com.example.quickbooksimporter.domain.ParsedCsvRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class BillRowMapper {
    public BillRowMapped map(ParsedCsvRow row, Map<NormalizedBillField, String> mapping) {
        return map(row, mapping, DateFormatOption.AUTO);
    }

    public BillRowMapped map(ParsedCsvRow row,
                             Map<NormalizedBillField, String> mapping,
                             DateFormatOption dateFormatOption) {
        DateFormatOption effective = dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption;
        BigDecimal qty = decimal(value(row.values(), mapping.get(NormalizedBillField.QUANTITY)));
        BigDecimal rate = decimal(value(row.values(), mapping.get(NormalizedBillField.RATE)));
        BigDecimal amount = decimal(value(row.values(), mapping.get(NormalizedBillField.AMOUNT)));
        if (amount == null && qty != null && rate != null) {
            amount = qty.multiply(rate);
        }
        if (qty == null) {
            qty = BigDecimal.ONE;
        }
        BillLine line = new BillLine(
                value(row.values(), mapping.get(NormalizedBillField.ITEM_NAME)),
                value(row.values(), mapping.get(NormalizedBillField.CATEGORY)),
                value(row.values(), mapping.get(NormalizedBillField.DESCRIPTION)),
                qty,
                rate,
                amount,
                "Y".equalsIgnoreCase(value(row.values(), mapping.get(NormalizedBillField.TAXABLE))),
                value(row.values(), mapping.get(NormalizedBillField.TAX_CODE)),
                decimal(value(row.values(), mapping.get(NormalizedBillField.TAX_RATE))));
        return new BillRowMapped(
                row.rowNumber(), row.values(),
                value(row.values(), mapping.get(NormalizedBillField.BILL_NO)),
                value(row.values(), mapping.get(NormalizedBillField.VENDOR)),
                date(value(row.values(), mapping.get(NormalizedBillField.TXN_DATE)), effective),
                date(value(row.values(), mapping.get(NormalizedBillField.DUE_DATE)), effective),
                value(row.values(), mapping.get(NormalizedBillField.AP_ACCOUNT)),
                line);
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
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid number: " + v); }
    }

    public record BillRowMapped(
            int rowNumber,
            Map<String, String> rawData,
            String billNo,
            String vendor,
            LocalDate txnDate,
            LocalDate dueDate,
            String apAccount,
            BillLine line) {}
}
