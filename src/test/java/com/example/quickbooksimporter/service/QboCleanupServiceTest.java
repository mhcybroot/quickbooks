package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QboCleanupServiceTest {

    @Mock
    private QuickBooksConnectionService connectionService;

    @Mock
    private QuickBooksGateway gateway;

    private QboCleanupService service;

    @BeforeEach
    void setUp() {
        service = new QboCleanupService(connectionService, gateway);
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("realm-1");
        connection.setConnectedAt(Instant.now());
        connection.setUpdatedAt(Instant.now());
        connection.setExpiresAt(Instant.now().plusSeconds(3600));
        when(connectionService.getActiveConnection()).thenReturn(connection);
    }

    @Test
    void invoiceListFetchesAllPagesByDefaultEvenWhenIncludeAllIsFalse() {
        QboCleanupFilter filter = new QboCleanupFilter(null, null, null, null, 2);
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 1))
                .thenReturn(List.of(row("1", "INV-1"), row("2", "INV-2")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 3))
                .thenReturn(List.of(row("3", "INV-3"), row("4", "INV-4")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 5))
                .thenReturn(List.of(row("5", "INV-5")));

        List<QboTransactionRow> result = service.list(QboCleanupEntityType.INVOICE, filter, false);

        assertEquals(5, result.size());
        verify(gateway, times(1)).listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 1);
        verify(gateway, times(1)).listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 3);
        verify(gateway, times(1)).listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 5);
    }

    @Test
    void partyFilterRunsAfterFullInvoiceAggregation() {
        QboCleanupFilter filter = new QboCleanupFilter(null, null, null, "beta", 2);
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 1))
                .thenReturn(List.of(row("1", "INV-1", "Alpha Co"), row("2", "INV-2", "Gamma Co")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 3))
                .thenReturn(List.of(row("3", "INV-3", "Beta Industries")));

        List<QboTransactionRow> result = service.list(QboCleanupEntityType.INVOICE, filter, false);

        assertEquals(1, result.size());
        assertEquals("INV-3", result.getFirst().externalNumber());
    }

    @Test
    void nonInvoiceListKeepsSinglePageBehaviorWhenIncludeAllIsFalse() {
        QboCleanupFilter filter = new QboCleanupFilter(null, null, null, null, 50);
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.BILL, filter, 1))
                .thenReturn(List.of(row("1", "BILL-1")));

        List<QboTransactionRow> result = service.list(QboCleanupEntityType.BILL, filter, false);

        assertEquals(1, result.size());
        verify(gateway, times(1)).listTransactions("realm-1", QboCleanupEntityType.BILL, filter, 1);
        verify(gateway, times(0)).listTransactions(eq("realm-1"), eq(QboCleanupEntityType.BILL), eq(filter), eq(51));
    }

    private QboTransactionRow row(String id, String externalNumber) {
        return row(id, externalNumber, "Test Party");
    }

    private QboTransactionRow row(String id, String externalNumber, String party) {
        return new QboTransactionRow(
                id,
                "0",
                QboCleanupEntityType.INVOICE,
                externalNumber,
                LocalDate.of(2026, 1, 1),
                party,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                "");
    }
}
