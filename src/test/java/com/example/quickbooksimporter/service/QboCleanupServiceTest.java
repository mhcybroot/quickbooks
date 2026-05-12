package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .thenReturn(List.of(row(QboCleanupEntityType.INVOICE, "1", "INV-1"), row(QboCleanupEntityType.INVOICE, "2", "INV-2")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 3))
                .thenReturn(List.of(row(QboCleanupEntityType.INVOICE, "3", "INV-3"), row(QboCleanupEntityType.INVOICE, "4", "INV-4")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 5))
                .thenReturn(List.of(row(QboCleanupEntityType.INVOICE, "5", "INV-5")));

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
                .thenReturn(List.of(
                        row(QboCleanupEntityType.INVOICE, "1", "INV-1", "Alpha Co"),
                        row(QboCleanupEntityType.INVOICE, "2", "INV-2", "Gamma Co")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.INVOICE, filter, 3))
                .thenReturn(List.of(row(QboCleanupEntityType.INVOICE, "3", "INV-3", "Beta Industries")));

        List<QboTransactionRow> result = service.list(QboCleanupEntityType.INVOICE, filter, false);

        assertEquals(1, result.size());
        assertEquals("INV-3", result.getFirst().externalNumber());
    }

    @Test
    void nonInvoiceListFetchesAllPagesByDefault() {
        QboCleanupFilter filter = new QboCleanupFilter(null, null, null, null, 2);
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.BILL, filter, 1))
                .thenReturn(List.of(row(QboCleanupEntityType.BILL, "1", "BILL-1"), row(QboCleanupEntityType.BILL, "2", "BILL-2")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.BILL, filter, 3))
                .thenReturn(List.of(row(QboCleanupEntityType.BILL, "3", "BILL-3")));

        List<QboTransactionRow> result = service.list(QboCleanupEntityType.BILL, filter, false);

        assertEquals(3, result.size());
        verify(gateway, times(1)).listTransactions("realm-1", QboCleanupEntityType.BILL, filter, 1);
        verify(gateway, times(1)).listTransactions("realm-1", QboCleanupEntityType.BILL, filter, 3);
    }

    @Test
    void partyFilterRunsAfterFullNonInvoiceAggregation() {
        QboCleanupFilter filter = new QboCleanupFilter(null, null, null, "north", 2);
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.BILL, filter, 1))
                .thenReturn(List.of(
                        row(QboCleanupEntityType.BILL, "1", "BILL-1", "East Supply"),
                        row(QboCleanupEntityType.BILL, "2", "BILL-2", "West Trade")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.BILL, filter, 3))
                .thenReturn(List.of(row(QboCleanupEntityType.BILL, "3", "BILL-3", "North Depot")));

        List<QboTransactionRow> result = service.list(QboCleanupEntityType.BILL, filter, false);

        assertEquals(1, result.size());
        assertEquals("BILL-3", result.getFirst().externalNumber());
    }

    @Test
    void includeAllTrueStillUsesSameAllPagePaginationPath() {
        QboCleanupFilter filter = new QboCleanupFilter(null, null, null, null, 2);
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.EXPENSE, filter, 1))
                .thenReturn(List.of(row(QboCleanupEntityType.EXPENSE, "1", "EXP-1"), row(QboCleanupEntityType.EXPENSE, "2", "EXP-2")));
        when(gateway.listTransactions("realm-1", QboCleanupEntityType.EXPENSE, filter, 3))
                .thenReturn(List.of(row(QboCleanupEntityType.EXPENSE, "3", "EXP-3")));

        List<QboTransactionRow> result = service.list(QboCleanupEntityType.EXPENSE, filter, true);

        assertEquals(3, result.size());
        verify(gateway, times(1)).listTransactions("realm-1", QboCleanupEntityType.EXPENSE, filter, 1);
        verify(gateway, times(1)).listTransactions("realm-1", QboCleanupEntityType.EXPENSE, filter, 3);
    }

    private QboTransactionRow row(QboCleanupEntityType type, String id, String externalNumber) {
        return row(type, id, externalNumber, "Test Party");
    }

    private QboTransactionRow row(QboCleanupEntityType type, String id, String externalNumber, String party) {
        return new QboTransactionRow(
                id,
                "0",
                type,
                externalNumber,
                LocalDate.of(2026, 1, 1),
                party,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                "");
    }
}
