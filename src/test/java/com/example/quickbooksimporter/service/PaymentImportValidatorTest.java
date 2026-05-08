package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedPayment;
import com.example.quickbooksimporter.domain.PaymentApplication;
import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentImportValidatorTest {

    @Mock
    private QuickBooksConnectionService connectionService;

    @Mock
    private QuickBooksGateway gateway;

    private PaymentImportValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentImportValidator(connectionService, gateway);
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("realm-1");
        connection.setConnectedAt(Instant.now());
        connection.setUpdatedAt(Instant.now());
        connection.setExpiresAt(Instant.now().plusSeconds(3600));
        when(connectionService.getConnection()).thenReturn(Optional.of(connection));
    }

    @Test
    void allowsPartialPaymentsThatCumulativelyMatchOpenBalance() {
        when(gateway.findInvoiceByDocNumber("realm-1", "INV-270101"))
                .thenReturn(new QuickBooksInvoiceRef("1", "INV-270101", "C1", "Evergreen Foods V3", new BigDecimal("450.00")));
        when(gateway.paymentExistsByReference("realm-1", "Evergreen Foods V3", LocalDate.of(2026, 7, 6), "RPV3-EVG-001")).thenReturn(false);
        when(gateway.paymentExistsByReference("realm-1", "Evergreen Foods V3", LocalDate.of(2026, 7, 12), "RPV3-EVG-002")).thenReturn(false);
        var first = validator.validate(1, Map.of(), payment("RPV3-EVG-001", LocalDate.of(2026, 7, 6), "250.00"), BigDecimal.ZERO);
        var second = validator.validate(2, Map.of(), payment("RPV3-EVG-002", LocalDate.of(2026, 7, 12), "200.00"), new BigDecimal("250.00"));

        assertEquals(ImportRowStatus.READY, first.status());
        assertEquals(ImportRowStatus.READY, second.status());
    }

    @Test
    void blocksWhenCumulativePaymentsExceedOpenBalance() {
        when(gateway.findInvoiceByDocNumber("realm-1", "INV-270101"))
                .thenReturn(new QuickBooksInvoiceRef("1", "INV-270101", "C1", "Evergreen Foods V3", new BigDecimal("450.00")));
        when(gateway.paymentExistsByReference("realm-1", "Evergreen Foods V3", LocalDate.of(2026, 7, 14), "RPV3-EVG-003")).thenReturn(false);
        var result = validator.validate(3, Map.of(), payment("RPV3-EVG-003", LocalDate.of(2026, 7, 14), "250.00"), new BigDecimal("250.00"));

        assertEquals(ImportRowStatus.INVALID, result.status());
        assertEquals("Applied amount exceeds invoice open balance", result.message());
    }

    @Test
    void prefersDraftInvoiceBalanceDuringBatchPreview() {
        when(gateway.paymentExistsByReference("realm-1", "Evergreen Foods V3", LocalDate.of(2026, 7, 6), "RPV3-DRAFT-001")).thenReturn(false);
        var result = validator.validate(
                1,
                Map.of(),
                draftPayment("RPV3-DRAFT-001", LocalDate.of(2026, 7, 6), "250.00", "INV-DRAFT-100"),
                BigDecimal.ZERO,
                new QuickBooksInvoiceRef("draft-1", "INV-DRAFT-100", "C1", "Evergreen Foods V3", new BigDecimal("450.00")));

        assertEquals(ImportRowStatus.READY, result.status());
        verify(gateway, never()).findInvoiceByDocNumber("realm-1", "INV-DRAFT-100");
    }

    private NormalizedPayment payment(String referenceNo, LocalDate paymentDate, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new NormalizedPayment(
                "Evergreen Foods V3",
                paymentDate,
                referenceNo,
                "Bank Transfer",
                "Checking",
                value,
                new PaymentApplication("INV-270101", value));
    }

    private NormalizedPayment draftPayment(String referenceNo, LocalDate paymentDate, String amount, String invoiceNo) {
        BigDecimal value = new BigDecimal(amount);
        return new NormalizedPayment(
                "Evergreen Foods V3",
                paymentDate,
                referenceNo,
                "Bank Transfer",
                "Checking",
                value,
                new PaymentApplication(invoiceNo, value));
    }
}
