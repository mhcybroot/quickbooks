package com.example.quickbooksimporter.domain;

import java.util.List;
import java.util.Map;

public record BillPaymentImportPreview(
        String sourceFileName,
        Map<NormalizedBillPaymentField, String> mapping,
        List<String> headers,
        List<BillPaymentImportPreviewRow> rows,
        List<BillPaymentRowValidationResult> validations) {
}
