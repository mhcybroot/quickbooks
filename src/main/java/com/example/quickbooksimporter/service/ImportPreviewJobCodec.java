package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.BillImportPreview;
import com.example.quickbooksimporter.domain.BillImportPreviewRow;
import com.example.quickbooksimporter.domain.BillPaymentImportPreview;
import com.example.quickbooksimporter.domain.BillPaymentImportPreviewRow;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ExpenseImportPreview;
import com.example.quickbooksimporter.domain.ExpenseImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportPreview;
import com.example.quickbooksimporter.domain.ImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.PaymentImportPreview;
import com.example.quickbooksimporter.domain.PaymentImportPreviewRow;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreview;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreviewRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ImportPreviewJobCodec {

    private final ObjectMapper objectMapper;

    public ImportPreviewJobCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ImportPreviewJobResult fromSummary(ImportPreviewSummary summary) {
        return new ImportPreviewJobResult(
                summary.entityType(),
                summary.sourceFileName(),
                summary.totalRows(),
                summary.readyRows(),
                summary.invalidRows(),
                summary.duplicateRows(),
                summary.exportCsv(),
                summary.warnings(),
                write(summary.rawPreview()));
    }

    public ImportPreviewSummary toSummary(ImportPreviewJobResult result, String suggestedProfileName) {
        return new ImportPreviewSummary(
                result.entityType(),
                result.sourceFileName(),
                List.of(),
                result.totalRows(),
                result.readyRows(),
                result.invalidRows(),
                result.duplicateRows(),
                result.exportCsv(),
                suggestedProfileName,
                result.warnings(),
                readRawPreview(result));
    }

    public ImportPreviewJobResult fromInvoicePreview(ImportPreview preview, List<String> warnings) {
        return new ImportPreviewJobResult(
                EntityType.INVOICE,
                preview.sourceFileName(),
                preview.rows().size(),
                readyCount(preview.rows()),
                invalidCount(preview.rows()),
                duplicateCount(preview.rows()),
                preview.exportCsv(),
                warnings,
                write(preview));
    }

    public ImportPreviewJobResult fromPaymentPreview(PaymentImportPreview preview, List<String> warnings) {
        return new ImportPreviewJobResult(
                EntityType.PAYMENT,
                preview.sourceFileName(),
                preview.rows().size(),
                readyCount(preview.rows()),
                invalidCount(preview.rows()),
                duplicateCount(preview.rows()),
                null,
                warnings,
                write(preview));
    }

    public ImportPreviewJobResult fromExpensePreview(ExpenseImportPreview preview, List<String> warnings) {
        return new ImportPreviewJobResult(
                EntityType.EXPENSE,
                preview.sourceFileName(),
                preview.rows().size(),
                readyCount(preview.rows()),
                invalidCount(preview.rows()),
                duplicateCount(preview.rows()),
                null,
                warnings,
                write(preview));
    }

    public ImportPreviewJobResult fromSalesReceiptPreview(SalesReceiptImportPreview preview, List<String> warnings) {
        return new ImportPreviewJobResult(
                EntityType.SALES_RECEIPT,
                preview.sourceFileName(),
                preview.rows().size(),
                readyCount(preview.rows()),
                invalidCount(preview.rows()),
                duplicateCount(preview.rows()),
                null,
                warnings,
                write(preview));
    }

    public ImportPreviewJobResult fromBillPreview(BillImportPreview preview, List<String> warnings) {
        return new ImportPreviewJobResult(
                EntityType.BILL,
                preview.sourceFileName(),
                preview.rows().size(),
                readyCount(preview.rows()),
                invalidCount(preview.rows()),
                duplicateCount(preview.rows()),
                null,
                warnings,
                write(preview));
    }

    public ImportPreviewJobResult fromBillPaymentPreview(BillPaymentImportPreview preview, List<String> warnings) {
        return new ImportPreviewJobResult(
                EntityType.BILL_PAYMENT,
                preview.sourceFileName(),
                preview.rows().size(),
                readyCount(preview.rows()),
                invalidCount(preview.rows()),
                duplicateCount(preview.rows()),
                null,
                warnings,
                write(preview));
    }

    public Object readRawPreview(ImportPreviewJobResult result) {
        return switch (result.entityType()) {
            case INVOICE -> read(result.rawPreviewJson(), ImportPreview.class);
            case PAYMENT -> read(result.rawPreviewJson(), PaymentImportPreview.class);
            case EXPENSE -> read(result.rawPreviewJson(), ExpenseImportPreview.class);
            case SALES_RECEIPT -> read(result.rawPreviewJson(), SalesReceiptImportPreview.class);
            case BILL -> read(result.rawPreviewJson(), BillImportPreview.class);
            case BILL_PAYMENT -> read(result.rawPreviewJson(), BillPaymentImportPreview.class);
        };
    }

    public ImportPreview readInvoicePreview(ImportPreviewJobResult result) {
        return read(result.rawPreviewJson(), ImportPreview.class);
    }

    public PaymentImportPreview readPaymentPreview(ImportPreviewJobResult result) {
        return read(result.rawPreviewJson(), PaymentImportPreview.class);
    }

    public ExpenseImportPreview readExpensePreview(ImportPreviewJobResult result) {
        return read(result.rawPreviewJson(), ExpenseImportPreview.class);
    }

    public SalesReceiptImportPreview readSalesReceiptPreview(ImportPreviewJobResult result) {
        return read(result.rawPreviewJson(), SalesReceiptImportPreview.class);
    }

    public BillImportPreview readBillPreview(ImportPreviewJobResult result) {
        return read(result.rawPreviewJson(), BillImportPreview.class);
    }

    public BillPaymentImportPreview readBillPaymentPreview(ImportPreviewJobResult result) {
        return read(result.rawPreviewJson(), BillPaymentImportPreview.class);
    }

    private int readyCount(List<? extends ImportPreviewSummary.ImportPreviewStatusRow> rows) {
        return (int) rows.stream().filter(row -> row.status() == ImportRowStatus.READY).count();
    }

    private int invalidCount(List<? extends ImportPreviewSummary.ImportPreviewStatusRow> rows) {
        return (int) rows.stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
    }

    private int duplicateCount(List<? extends ImportPreviewSummary.ImportPreviewStatusRow> rows) {
        return ImportPreviewSummary.duplicateCount(rows);
    }

    private String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize preview payload", exception);
        }
    }

    private <T> T read(String payload, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payload, payloadType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize preview payload", exception);
        }
    }
}
