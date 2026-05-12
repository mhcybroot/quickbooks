package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.domain.ParsedCsvRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class InvoiceRowMapper {

    public NormalizedInvoice map(ParsedCsvRow row, Map<NormalizedInvoiceField, String> mapping) {
        return map(row, mapping, DateFormatOption.AUTO);
    }

    public NormalizedInvoice map(ParsedCsvRow row,
                                 Map<NormalizedInvoiceField, String> mapping,
                                 DateFormatOption dateFormatOption) {
        DateFormatOption effective = dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption;
        InvoiceLine line = new InvoiceLine(
                get(row, mapping, NormalizedInvoiceField.ITEM_NAME),
                get(row, mapping, NormalizedInvoiceField.ITEM_DESCRIPTION),
                decimal(get(row, mapping, NormalizedInvoiceField.ITEM_QUANTITY)),
                decimal(get(row, mapping, NormalizedInvoiceField.ITEM_RATE)),
                decimal(get(row, mapping, NormalizedInvoiceField.ITEM_AMOUNT)),
                "Y".equalsIgnoreCase(get(row, mapping, NormalizedInvoiceField.TAXABLE)),
                percent(get(row, mapping, NormalizedInvoiceField.TAX_RATE)),
                date(get(row, mapping, NormalizedInvoiceField.SERVICE_DATE), effective));
        return new NormalizedInvoice(
                get(row, mapping, NormalizedInvoiceField.INVOICE_NO),
                get(row, mapping, NormalizedInvoiceField.CUSTOMER),
                date(get(row, mapping, NormalizedInvoiceField.INVOICE_DATE), effective),
                date(get(row, mapping, NormalizedInvoiceField.DUE_DATE), effective),
                get(row, mapping, NormalizedInvoiceField.TERMS),
                get(row, mapping, NormalizedInvoiceField.LOCATION),
                get(row, mapping, NormalizedInvoiceField.MEMO),
                List.of(line));
    }

    private String get(ParsedCsvRow row, Map<NormalizedInvoiceField, String> mapping, NormalizedInvoiceField field) {
        String header = mapping.get(field);
        if (header == null) {
            return null;
        }
        return StringUtils.trimToNull(row.values().get(header));
    }

    private LocalDate date(String value, DateFormatOption option) {
        if (value == null) {
            return null;
        }
        return option.parse(value.trim());
    }

    private BigDecimal decimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number: " + value);
        }
    }

    private BigDecimal percent(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace("%", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid tax rate: " + value);
        }
    }
}
