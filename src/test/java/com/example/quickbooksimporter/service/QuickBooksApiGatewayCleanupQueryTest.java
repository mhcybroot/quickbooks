package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    @Test
    void buildCreateBatchChunksSplitsRequestsIntoGroupsOfTen() {
        QuickBooksApiGateway gateway = new QuickBooksApiGateway(null, null, null, null);

        List<List<QuickBooksApiGateway.BatchItemRequest>> chunks = gateway.buildCreateBatchChunks(
                "Invoice",
                java.util.stream.IntStream.rangeClosed(1, 11)
                        .mapToObj(index -> Map.<String, Object>of("DocNumber", "INV-" + index))
                        .toList());

        assertEquals(2, chunks.size());
        assertEquals(10, chunks.getFirst().size());
        assertEquals(1, chunks.get(1).size());
        assertEquals("1", chunks.getFirst().getFirst().bId());
        assertEquals("11", chunks.get(1).getFirst().bId());
    }

    @Test
    void mapBatchCreateResultsPreservesOrderAndParsesFailures() {
        QuickBooksApiGateway gateway = new QuickBooksApiGateway(null, null, null, null);
        List<QuickBooksApiGateway.BatchItemRequest> chunk = List.of(
                new QuickBooksApiGateway.BatchItemRequest("1", "create", Map.of("Invoice", Map.of("DocNumber", "INV-1"))),
                new QuickBooksApiGateway.BatchItemRequest("2", "create", Map.of("Invoice", Map.of("DocNumber", "INV-2"))));
        QuickBooksApiGateway.BatchResponse response = new QuickBooksApiGateway.BatchResponse(List.of(
                Map.of("bId", "2", "Fault", Map.of("Error", List.of(Map.of("Message", "Duplicate", "Detail", "Invoice already exists")))),
                Map.of("bId", "1", "Invoice", Map.of("Id", "100", "DocNumber", "INV-1"))));

        List<QuickBooksBatchCreateResult> results = gateway.mapBatchCreateResults("Invoice", chunk, response, "tid-1");

        assertEquals(2, results.size());
        assertTrue(results.getFirst().success());
        assertEquals("100", results.getFirst().entityId());
        assertEquals("INV-1", results.getFirst().referenceNumber());
        assertTrue(!results.get(1).success());
        assertEquals("Duplicate: Invoice already exists", results.get(1).message());
        assertEquals("tid-1", results.get(1).intuitTid());
    }
}
