package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuickBooksApiGatewayCleanupQueryTest {

    @Test
    void cleanupListQueryIncludesPagingFilteringAndOrdering() {
        QuickBooksApiGateway gateway = new QuickBooksApiGateway(null, null, null, null);
        QboCleanupFilter filter = new QboCleanupFilter(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                "INV",
                "Ignored local party filter",
                "PAID",
                new BigDecimal("100"),
                new BigDecimal("500"),
                new BigDecimal("0"),
                new BigDecimal("50"),
                "123",
                QboCleanupSortField.TOTAL_AMOUNT,
                QboSortDirection.ASC,
                200);

        String query = gateway.buildCleanupListQuery(QboCleanupEntityType.INVOICE, filter, 201, 200);

        assertTrue(query.contains("from Invoice"));
        assertTrue(query.contains("TxnDate >= '2026-01-01'"));
        assertTrue(query.contains("TxnDate <= '2026-01-31'"));
        assertTrue(query.contains("DocNumber LIKE '%INV%'"));
        assertTrue(query.contains("PrivateNote LIKE '%PAID%'"));
        assertTrue(query.contains("TotalAmt >= '100'"));
        assertTrue(query.contains("TotalAmt <= '500'"));
        assertTrue(query.contains("Balance >= '0'"));
        assertTrue(query.contains("Balance <= '50'"));
        assertTrue(query.contains("Id LIKE '%123%'"));
        assertTrue(query.contains("order by TotalAmt asc, Id desc"));
        assertTrue(query.contains("startposition 201"));
        assertTrue(query.contains("maxresults 200"));
    }

    @Test
    void cleanupListQueryForNonInvoiceEntityIncludesPagingFilteringAndOrdering() {
        QuickBooksApiGateway gateway = new QuickBooksApiGateway(null, null, null, null);
        QboCleanupFilter filter = new QboCleanupFilter(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                "BP-",
                "Ignored local party filter",
                null,
                null,
                null,
                new BigDecimal("1"),
                new BigDecimal("20"),
                null,
                QboCleanupSortField.BALANCE,
                QboSortDirection.DESC,
                100);

        String query = gateway.buildCleanupListQuery(QboCleanupEntityType.RECEIVE_PAYMENT, filter, 101, 100);

        assertTrue(query.contains("from Payment"));
        assertTrue(query.contains("TxnDate >= '2026-02-01'"));
        assertTrue(query.contains("TxnDate <= '2026-02-28'"));
        assertTrue(query.contains("PaymentRefNum LIKE '%BP-%'"));
        assertTrue(!query.contains("UnappliedAmt >="));
        assertTrue(!query.contains("UnappliedAmt <="));
        assertTrue(query.contains("order by TxnDate desc, Id desc"));
        assertTrue(query.contains("startposition 101"));
        assertTrue(query.contains("maxresults 100"));
    }
}
