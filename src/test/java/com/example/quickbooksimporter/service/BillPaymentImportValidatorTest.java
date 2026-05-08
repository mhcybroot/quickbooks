package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.BillPaymentApplication;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedBillPayment;
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
class BillPaymentImportValidatorTest {

    @Mock
    private QuickBooksConnectionService connectionService;

    @Mock
    private QuickBooksGateway gateway;

    private BillPaymentImportValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BillPaymentImportValidator(connectionService, gateway);
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("realm-1");
        connection.setConnectedAt(Instant.now());
        connection.setUpdatedAt(Instant.now());
        connection.setExpiresAt(Instant.now().plusSeconds(3600));
        when(connectionService.getConnection()).thenReturn(Optional.of(connection));
        when(gateway.findBillByDocNumber("realm-1", "BILLV3-270801"))
                .thenReturn(new QuickBooksBillRef("1", "BILLV3-270801", "V1", "Supply Chain Hub V3", new BigDecimal("280.00")));
        when(gateway.findAccountIdByName("realm-1", "Checking")).thenReturn("acct-1");
        when(gateway.billPaymentExists("realm-1", "Supply Chain Hub V3", LocalDate.of(2026, 7, 14), "BPV3-001", new BigDecimal("140.00"))).thenReturn(false);
        when(gateway.billPaymentExists("realm-1", "Supply Chain Hub V3", LocalDate.of(2026, 7, 18), "BPV3-002", new BigDecimal("140.00"))).thenReturn(false);
    }

    @Test
    void allowsPartialBillPaymentsThatSumToOpenBalance() {
        var first = validator.validate(1, Map.of(), payment("BPV3-001", LocalDate.of(2026, 7, 14), "140.00"), BigDecimal.ZERO);
        var second = validator.validate(2, Map.of(), payment("BPV3-002", LocalDate.of(2026, 7, 18), "140.00"), new BigDecimal("140.00"));

        assertEquals(ImportRowStatus.READY, first.status());
        assertEquals(ImportRowStatus.READY, second.status());
    }

    private NormalizedBillPayment payment(String referenceNo, LocalDate paymentDate, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new NormalizedBillPayment(
                "Supply Chain Hub V3",
                paymentDate,
                referenceNo,
                "Checking",
                value,
                new BillPaymentApplication("BILLV3-270801", value));
    }
}
