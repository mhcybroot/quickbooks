package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.ParsedCsvRow;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import com.example.quickbooksimporter.persistence.ReconciliationSessionEntity;
import com.example.quickbooksimporter.repository.ReconciliationSessionRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private InvoiceCsvParser parser;

    @Mock
    private QuickBooksConnectionService connectionService;

    @Mock
    private QuickBooksGateway gateway;

    @Mock
    private ReconciliationSessionRepository sessionRepository;

    @Mock
    private CurrentCompanyService currentCompanyService;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(parser, connectionService, gateway, sessionRepository, currentCompanyService);
        lenient().when(currentCompanyService.requireCurrentCompanyId()).thenReturn(1L);
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("realm-1");
        connection.setConnectedAt(Instant.now());
        connection.setUpdatedAt(Instant.now());
        connection.setExpiresAt(Instant.now().plusSeconds(3600));
        lenient().when(connectionService.getActiveConnection()).thenReturn(connection);
        CompanyEntity company = new CompanyEntity();
        lenient().when(currentCompanyService.requireCurrentCompany()).thenReturn(company);
    }

    @Test
    void previewMatchesClassifiesTier1AndBankOnlyRows() throws Exception {
        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("Date", "08/01/2026");
        row1.put("Amount", "120.00");
        row1.put("Ref", "ABC-100");
        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("Date", "08/01/2026");
        row2.put("Amount", "90.00");
        row2.put("Ref", "NO-MATCH");

        ParsedCsvDocument document = new ParsedCsvDocument(
                List.of("Date", "Amount", "Ref"),
                List.of(new ParsedCsvRow(2, row1), new ParsedCsvRow(3, row2)));
        when(parser.parse(any())).thenReturn(document);

        when(gateway.listReconciliationCandidates(eq("realm-1"), any(), any())).thenReturn(List.of(
                new QboReconCandidate("txn-1", "0", "Payment", LocalDate.of(2026, 8, 1), new BigDecimal("120.00"), "ABC-100", "Acme", "")));

        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ReconciliationSessionEntity entity = invocation.getArgument(0);
            Field idField = ReconciliationSessionEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, 10L);
            return entity;
        });

        ReconciliationPreview preview = service.previewMatches(
                "bank.csv",
                "x".getBytes(),
                new ReconciliationService.ReconciliationColumnMapping("Date", "Amount", null, null, "Ref", null, null),
                true,
                3);

        assertEquals(10L, preview.sessionId());
        assertEquals(1, preview.autoMatched().size());
        assertEquals(ReconciliationTier.TIER1, preview.autoMatched().getFirst().tier());
        assertFalse(preview.autoMatched().getFirst().batch());
        assertEquals("UNKNOWN", preview.autoMatched().getFirst().patternType());
        assertEquals(1, preview.bankOnly().size());
    }

    @Test
    void previewMatchesPatternDrivenZelleRow() throws Exception {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Date", "08/01/2026");
        row.put("Amount", "120.00");
        row.put("Ref", "");
        row.put("Memo", "Zelle payment to FINARA PROPERTY SOLUTIONS LLC for Finara Crew payment; Conf# uscwy55g0");

        ParsedCsvDocument document = new ParsedCsvDocument(
                List.of("Date", "Amount", "Ref", "Memo"),
                List.of(new ParsedCsvRow(2, row)));
        when(parser.parse(any())).thenReturn(document);

        when(gateway.listReconciliationCandidates(eq("realm-1"), any(), any())).thenReturn(List.of(
                new QboReconCandidate("txn-z", "0", "Payment", LocalDate.of(2026, 8, 2), new BigDecimal("120.00"), "", "FINARA PROPERTY SOLUTIONS LLC", "Conf# uscwy55g0")));

        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ReconciliationSessionEntity entity = invocation.getArgument(0);
            Field idField = ReconciliationSessionEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, 11L);
            return entity;
        });

        ReconciliationPreview preview = service.previewMatches(
                "bank.csv",
                "x".getBytes(),
                new ReconciliationService.ReconciliationColumnMapping("Date", "Amount", null, null, "Ref", "Memo", null),
                true,
                20);

        assertEquals(1, preview.autoMatched().size());
        assertEquals("ZELLE", preview.autoMatched().getFirst().patternType());
    }

    @Test
    void applyMatchesWritesBackAndExportsCsv() throws Exception {
        ReconciliationSessionEntity session = new ReconciliationSessionEntity();
        Field idField = ReconciliationSessionEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(session, 21L);
        session.setSourceFileName("bank.csv");
        session.setDryRun(false);
        session.setStatus("PREVIEWED");
        session.setCreatedAt(Instant.now());

        com.example.quickbooksimporter.persistence.ReconciliationSessionRowEntity row = new com.example.quickbooksimporter.persistence.ReconciliationSessionRowEntity();
        row.setSession(session);
        row.setBankRowNumber(5);
        row.setQboTxnId("txn-5");
        row.setQboSyncToken("1");
        row.setQboEntityType("Payment");
        row.setQboTxnDate(LocalDate.of(2026, 8, 2));
        row.setQboAmount(new BigDecimal("100.00"));
        row.setQboReference("REF-5");
        row.setQboParty("Acme");
        row.setTier("TIER1");
        row.setConfidence(100);
        row.setDisposition(ReconciliationDisposition.AUTO_MATCHED.name());
        row.setRationale("Exact");
        row.setCandidateTxnIds("txn-5");
        row.setCandidateCount(1);
        row.setGroupKey("ref|acme");
        row.setAllocationMode("SINGLE");
        row.setBatchMatch(false);
        session.getRows().add(row);

        when(sessionRepository.findByIdAndCompanyId(21L, 1L)).thenReturn(Optional.of(session));
        when(gateway.markTransactionReconciled(eq("realm-1"), any(), any()))
                .thenReturn(new QuickBooksReconcileMarkResult(true, "ok", "tid-1"));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationApplyResult result = service.applyMatches(21L, List.of());

        assertTrue(result.success());
        assertEquals(1, result.appliedRows().size());
        assertEquals(ReconciliationDisposition.APPLIED, result.appliedRows().getFirst().disposition());
        assertEquals(1, result.appliedRows().getFirst().candidateCount());

        String csv = service.exportSessionCsv(21L);
        assertTrue(csv.contains("sessionId,sourceFileName"));
        assertTrue(csv.contains("bank.csv"));
        assertTrue(csv.contains("txn-5"));
        assertTrue(csv.contains("APPLIED"));
        assertTrue(csv.contains("candidateTxnIds"));
        assertTrue(csv.contains("patternType"));
        assertNotNull(service.exportFileName(21L));
        assertFalse(service.exportFileName(21L).isBlank());
    }
}
