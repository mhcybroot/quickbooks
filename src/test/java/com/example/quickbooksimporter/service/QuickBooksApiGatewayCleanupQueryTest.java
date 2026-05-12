package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
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
                200);

        String query = gateway.buildCleanupListQuery(QboCleanupEntityType.INVOICE, filter, 201, 200);

        assertTrue(query.contains("from Invoice"));
        assertTrue(query.contains("TxnDate >= '2026-01-01'"));
        assertTrue(query.contains("TxnDate <= '2026-01-31'"));
        assertTrue(query.contains("DocNumber LIKE '%INV%'"));
        assertTrue(query.contains("order by TxnDate desc, Id desc"));
        assertTrue(query.contains("startposition 201"));
        assertTrue(query.contains("maxresults 200"));
    }
}
