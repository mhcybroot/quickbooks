package com.example.quickbooksimporter.domain;

import java.util.List;
import java.util.Map;

public record PaymentImportPreview(
        String sourceFileName,
        Map<NormalizedPaymentField, String> mapping,
        List<String> headers,
        List<PaymentImportPreviewRow> rows,
        List<PaymentRowValidationResult> validations) {
}
