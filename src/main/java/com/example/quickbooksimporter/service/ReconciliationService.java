package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.ParsedCsvRow;
import com.example.quickbooksimporter.persistence.ReconciliationSessionEntity;
import com.example.quickbooksimporter.persistence.ReconciliationSessionRowEntity;
import com.example.quickbooksimporter.repository.ReconciliationSessionRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationService {

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private final InvoiceCsvParser parser;
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway quickBooksGateway;
    private final ReconciliationSessionRepository sessionRepository;

    public ReconciliationService(InvoiceCsvParser parser,
                                 QuickBooksConnectionService connectionService,
                                 QuickBooksGateway quickBooksGateway,
                                 ReconciliationSessionRepository sessionRepository) {
        this.parser = parser;
        this.connectionService = connectionService;
        this.quickBooksGateway = quickBooksGateway;
        this.sessionRepository = sessionRepository;
    }

    public ReconciliationPreview previewMatches(String fileName,
                                                byte[] fileBytes,
                                                ReconciliationColumnMapping mapping,
                                                boolean dryRun,
                                                int dateWindowDays) {
        int windowDays = dateWindowDays <= 0 ? 20 : dateWindowDays;
        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(fileBytes));
        List<BankStatementRow> bankRows = document.rows().stream()
                .map(row -> toBankRow(row, mapping))
                .filter(Objects::nonNull)
                .toList();
        LocalDate from = bankRows.stream().map(BankStatementRow::txnDate).filter(Objects::nonNull).min(LocalDate::compareTo)
                .orElse(LocalDate.now().minusDays(31)).minusDays(windowDays);
        LocalDate to = bankRows.stream().map(BankStatementRow::txnDate).filter(Objects::nonNull).max(LocalDate::compareTo)
                .orElse(LocalDate.now()).plusDays(windowDays);

        String realmId = connectionService.getActiveConnection().getRealmId();
        List<QboReconCandidate> candidates = quickBooksGateway.listReconciliationCandidates(realmId, from, to);

        Set<String> usedCandidateIds = new HashSet<>();
        List<ReconciliationMatchResult> autoMatched = new ArrayList<>();
        List<ReconciliationMatchResult> needsReview = new ArrayList<>();
        List<ReconciliationMatchResult> bankOnly = new ArrayList<>();

        for (BankStatementRow bankRow : bankRows) {
            BatchDecision batchDecision = matchBatchByReference(bankRow, candidates, windowDays, usedCandidateIds);
            if (batchDecision != null) {
                if (batchDecision.auto) {
                    autoMatched.add(batchDecision.result);
                } else {
                    needsReview.add(batchDecision.result);
                }
                usedCandidateIds.addAll(batchDecision.result.candidates().stream().map(QboReconCandidate::txnId).toList());
                continue;
            }

            MatchDecision decision = scoreBest(bankRow, candidates, windowDays);
            if (decision == null || decision.candidate == null) {
                bankOnly.add(new ReconciliationMatchResult(
                        bankRow.rowNumber(),
                        ReconciliationTier.NONE,
                        0,
                        ReconciliationDisposition.BANK_ONLY,
                        "No suitable QBO candidate found",
                        null,
                        List.of(),
                        groupKey(bankRow.reference(), bankRow.counterparty()),
                        false,
                        "NONE",
                        bankRow.txnDate(),
                        bankRow.txnDate()));
                continue;
            }
            if (usedCandidateIds.contains(decision.candidate.txnId())) {
                needsReview.add(new ReconciliationMatchResult(
                        bankRow.rowNumber(),
                        decision.tier,
                        decision.confidence,
                        ReconciliationDisposition.NEEDS_REVIEW,
                        "Candidate already used by another bank row",
                        decision.candidate,
                        List.of(decision.candidate),
                        groupKey(bankRow.reference(), bankRow.counterparty()),
                        false,
                        "SINGLE",
                        minDate(List.of(decision.candidate)),
                        maxDate(List.of(decision.candidate))));
                continue;
            }
            usedCandidateIds.add(decision.candidate.txnId());
            if (decision.confidence >= 90 && decision.tier != ReconciliationTier.TIER3) {
                autoMatched.add(new ReconciliationMatchResult(
                        bankRow.rowNumber(),
                        decision.tier,
                        decision.confidence,
                        ReconciliationDisposition.AUTO_MATCHED,
                        decision.reason,
                        decision.candidate,
                        List.of(decision.candidate),
                        groupKey(bankRow.reference(), bankRow.counterparty()),
                        false,
                        "SINGLE",
                        minDate(List.of(decision.candidate)),
                        maxDate(List.of(decision.candidate))));
            } else {
                needsReview.add(new ReconciliationMatchResult(
                        bankRow.rowNumber(),
                        decision.tier,
                        decision.confidence,
                        ReconciliationDisposition.NEEDS_REVIEW,
                        decision.reason,
                        decision.candidate,
                        List.of(decision.candidate),
                        groupKey(bankRow.reference(), bankRow.counterparty()),
                        false,
                        "SINGLE",
                        minDate(List.of(decision.candidate)),
                        maxDate(List.of(decision.candidate))));
            }
        }

        Set<String> matchedCandidates = new HashSet<>();
        autoMatched.forEach(result -> result.candidates().forEach(candidate -> matchedCandidates.add(candidate.txnId())));
        needsReview.forEach(result -> result.candidates().forEach(candidate -> matchedCandidates.add(candidate.txnId())));
        List<QboReconCandidate> qboOnly = candidates.stream().filter(candidate -> !matchedCandidates.contains(candidate.txnId())).toList();

        ReconciliationSessionEntity session = new ReconciliationSessionEntity();
        session.setSourceFileName(StringUtils.defaultIfBlank(fileName, "bank-statement.csv"));
        session.setDryRun(dryRun);
        session.setStatus("PREVIEWED");
        session.setCreatedAt(Instant.now());
        session.setAutoMatchedCount(autoMatched.size());
        session.setNeedsReviewCount(needsReview.size());
        session.setBankOnlyCount(bankOnly.size());
        session.setQboOnlyCount(qboOnly.size());
        session.setAppliedCount(0);
        session.setFailedCount(0);

        Map<Integer, BankStatementRow> bankByRow = new HashMap<>();
        bankRows.forEach(row -> bankByRow.put(row.rowNumber(), row));

        List<ReconciliationMatchResult> all = new ArrayList<>();
        all.addAll(autoMatched);
        all.addAll(needsReview);
        all.addAll(bankOnly);

        for (ReconciliationMatchResult result : all) {
            ReconciliationSessionRowEntity row = new ReconciliationSessionRowEntity();
            row.setSession(session);
            row.setBankRowNumber(result.bankRowNumber());
            BankStatementRow bank = bankByRow.get(result.bankRowNumber());
            if (bank != null) {
                row.setBankTxnDate(bank.txnDate());
                row.setBankAmount(bank.signedAmount());
                row.setBankReference(bank.reference());
                row.setBankMemo(bank.memo());
                row.setBankCounterparty(bank.counterparty());
            }
            if (result.candidate() != null) {
                row.setQboTxnId(result.candidate().txnId());
                row.setQboSyncToken(result.candidate().syncToken());
                row.setQboEntityType(result.candidate().entityType());
                row.setQboTxnDate(result.candidate().txnDate());
                row.setQboAmount(result.candidate().signedAmount());
                row.setQboReference(result.candidate().reference());
                row.setQboParty(result.candidate().party());
            }
            row.setCandidateTxnIds(result.candidateTxnIds());
            row.setCandidateCount(result.candidateCount());
            row.setGroupKey(result.groupKey());
            row.setGroupWindowStart(result.groupWindowStart());
            row.setGroupWindowEnd(result.groupWindowEnd());
            row.setAllocationMode(result.allocationMode());
            row.setBatchMatch(result.batch());
            row.setTier(result.tier().name());
            row.setConfidence(result.confidence());
            row.setDisposition(result.disposition().name());
            row.setRationale(result.rationale());
            row.setApplied(false);
            row.setApplySuccess(false);
            row.setApplyFailCount(0);
            row.setApplySuccessCount(0);
            session.getRows().add(row);
        }

        ReconciliationSessionEntity saved = sessionRepository.save(session);
        return new ReconciliationPreview(saved.getId(), autoMatched, needsReview, bankOnly, qboOnly);
    }

    public ReconciliationApplyResult applyMatches(Long sessionId, List<Integer> selectedBankRows) {
        ReconciliationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation session not found"));
        String realmId = connectionService.getActiveConnection().getRealmId();
        Set<Integer> selected = selectedBankRows == null ? Set.of() : new HashSet<>(selectedBankRows);
        Set<String> appliedTxnInCall = new HashSet<>();
        int applied = 0;
        int failed = 0;
        List<ReconciliationMatchResult> outcomes = new ArrayList<>();

        for (ReconciliationSessionRowEntity row : session.getRows()) {
            ReconciliationDisposition disposition = parseDisposition(row.getDisposition());
            boolean shouldApply = disposition == ReconciliationDisposition.AUTO_MATCHED
                    || (disposition == ReconciliationDisposition.NEEDS_REVIEW && selected.contains(row.getBankRowNumber()));
            if (!shouldApply) {
                continue;
            }

            List<QboReconCandidate> rowCandidates = resolveRowCandidates(row);
            if (rowCandidates.isEmpty()) {
                continue;
            }

            int successCount = 0;
            int failCount = 0;
            String lastTid = null;
            String lastMessage = null;

            for (QboReconCandidate candidate : rowCandidates) {
                if (appliedTxnInCall.contains(candidate.txnId())) {
                    continue;
                }
                appliedTxnInCall.add(candidate.txnId());
                String note = "RECON_SESSION:" + sessionId + ":GROUP:" + StringUtils.defaultString(row.getGroupKey(), "NA")
                        + ":BANK_ROW:" + row.getBankRowNumber();
                QuickBooksReconcileMarkResult mark = quickBooksGateway.markTransactionReconciled(realmId, candidate, note);
                lastTid = mark.intuitTid();
                lastMessage = mark.message();
                if (mark.success()) {
                    successCount++;
                    applied++;
                } else {
                    failCount++;
                    failed++;
                }
            }

            row.setApplied(true);
            row.setApplySuccess(successCount > 0 && failCount == 0);
            row.setApplySuccessCount(row.getApplySuccessCount() + successCount);
            row.setApplyFailCount(row.getApplyFailCount() + failCount);
            row.setIntuitTid(lastTid);
            row.setApplyMessage(lastMessage);

            if (failCount == 0 && successCount > 0) {
                row.setDisposition(ReconciliationDisposition.APPLIED.name());
            } else if (successCount > 0) {
                row.setDisposition(ReconciliationDisposition.PARTIAL_APPLY_FAILED.name());
            } else {
                row.setDisposition(ReconciliationDisposition.APPLY_FAILED.name());
            }

            outcomes.add(new ReconciliationMatchResult(
                    row.getBankRowNumber(),
                    parseTier(row.getTier()),
                    row.getConfidence(),
                    parseDisposition(row.getDisposition()),
                    StringUtils.defaultIfBlank(row.getApplyMessage(), row.getRationale()),
                    rowCandidates.getFirst(),
                    rowCandidates,
                    row.getGroupKey(),
                    row.isBatchMatch(),
                    StringUtils.defaultString(row.getAllocationMode(), "NONE"),
                    row.getGroupWindowStart(),
                    row.getGroupWindowEnd()));
        }

        session.setAppliedCount(session.getAppliedCount() + applied);
        session.setFailedCount(session.getFailedCount() + failed);
        session.setStatus(failed == 0 ? "APPLIED" : "PARTIAL_FAILURE");
        session.setCompletedAt(Instant.now());
        sessionRepository.save(session);

        return new ReconciliationApplyResult(failed == 0,
                "Applied: " + applied + ", Failed: " + failed,
                outcomes);
    }

    public String exportSessionCsv(Long sessionId) {
        ReconciliationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation session not found"));
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("sessionId", "sourceFileName", "status", "dryRun", "createdAt", "completedAt",
                        "autoMatchedCount", "needsReviewCount", "bankOnlyCount", "qboOnlyCount", "appliedCount", "failedCount",
                        "bankRowNumber", "bankTxnDate", "bankAmount", "bankReference", "bankMemo", "bankCounterparty",
                        "qboTxnId", "qboEntityType", "qboTxnDate", "qboAmount", "qboReference", "qboParty",
                        "groupKey", "isBatch", "candidateTxnIds", "candidateCount", "windowDays", "allocationMode",
                        "tier", "confidence", "disposition", "rationale", "applied", "applySuccess", "applySuccessCount", "applyFailCount", "intuitTid", "applyMessage")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            if (session.getRows().isEmpty()) {
                printer.printRecord(session.getId(), session.getSourceFileName(), session.getStatus(), session.isDryRun(), session.getCreatedAt(), session.getCompletedAt(),
                        session.getAutoMatchedCount(), session.getNeedsReviewCount(), session.getBankOnlyCount(), session.getQboOnlyCount(), session.getAppliedCount(), session.getFailedCount(),
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null);
            } else {
                List<ReconciliationSessionRowEntity> sorted = session.getRows().stream()
                        .sorted(Comparator.comparingInt(ReconciliationSessionRowEntity::getBankRowNumber))
                        .toList();
                for (ReconciliationSessionRowEntity row : sorted) {
                    int windowDays = row.getGroupWindowStart() == null || row.getGroupWindowEnd() == null ? 0
                            : (int) Math.abs(row.getGroupWindowEnd().toEpochDay() - row.getGroupWindowStart().toEpochDay());
                    printer.printRecord(session.getId(), session.getSourceFileName(), session.getStatus(), session.isDryRun(), session.getCreatedAt(), session.getCompletedAt(),
                            session.getAutoMatchedCount(), session.getNeedsReviewCount(), session.getBankOnlyCount(), session.getQboOnlyCount(), session.getAppliedCount(), session.getFailedCount(),
                            row.getBankRowNumber(), row.getBankTxnDate(), row.getBankAmount(), row.getBankReference(), row.getBankMemo(), row.getBankCounterparty(),
                            row.getQboTxnId(), row.getQboEntityType(), row.getQboTxnDate(), row.getQboAmount(), row.getQboReference(), row.getQboParty(),
                            row.getGroupKey(), row.isBatchMatch(), row.getCandidateTxnIds(), row.getCandidateCount(), windowDays, row.getAllocationMode(),
                            row.getTier(), row.getConfidence(), row.getDisposition(), row.getRationale(), row.isApplied(), row.isApplySuccess(), row.getApplySuccessCount(), row.getApplyFailCount(), row.getIntuitTid(), row.getApplyMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export reconciliation CSV", e);
        }
        return writer.toString();
    }

    public Optional<ReconciliationSessionEntity> findSession(Long sessionId) {
        return sessionRepository.findById(sessionId);
    }

    public String exportFileName(Long sessionId) {
        return "reconciliation-session-" + sessionId + ".csv";
    }

    private BankStatementRow toBankRow(ParsedCsvRow row, ReconciliationColumnMapping mapping) {
        LocalDate date = parseDate(read(row, mapping.txnDateHeader()));
        BigDecimal amount = parseAmount(read(row, mapping.amountHeader()));
        if (amount == null) {
            BigDecimal debit = parseAmount(read(row, mapping.debitHeader()));
            BigDecimal credit = parseAmount(read(row, mapping.creditHeader()));
            if (debit != null) {
                amount = debit.negate();
            } else if (credit != null) {
                amount = credit;
            }
        }
        if (date == null || amount == null) {
            return null;
        }
        return new BankStatementRow(
                row.rowNumber(),
                date,
                amount,
                read(row, mapping.referenceHeader()),
                read(row, mapping.memoHeader()),
                read(row, mapping.counterpartyHeader()));
    }

    private BatchDecision matchBatchByReference(BankStatementRow bank,
                                                List<QboReconCandidate> candidates,
                                                int dateWindowDays,
                                                Set<String> alreadyUsed) {
        String refNorm = normalizeToken(bank.reference());
        if (StringUtils.isBlank(refNorm)) {
            return null;
        }
        String partyNorm = normalizeToken(bank.counterparty());
        LocalDate from = bank.txnDate().minusDays(dateWindowDays);
        LocalDate to = bank.txnDate().plusDays(dateWindowDays);

        List<QboReconCandidate> group = candidates.stream()
                .filter(candidate -> !alreadyUsed.contains(candidate.txnId()))
                .filter(candidate -> refNorm.equals(normalizeToken(candidate.reference())))
                .filter(candidate -> candidate.txnDate() != null && !candidate.txnDate().isBefore(from) && !candidate.txnDate().isAfter(to))
                .filter(candidate -> StringUtils.isBlank(partyNorm) || normalizeToken(candidate.party()).contains(partyNorm) || partyNorm.contains(normalizeToken(candidate.party())))
                .sorted(Comparator.comparing(QboReconCandidate::txnDate).thenComparing(QboReconCandidate::txnId))
                .toList();

        if (group.isEmpty()) {
            return null;
        }

        BigDecimal total = group.stream().map(QboReconCandidate::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String gk = groupKey(bank.reference(), bank.counterparty());

        if (group.size() > 1 && total.compareTo(bank.signedAmount()) == 0) {
            ReconciliationMatchResult result = new ReconciliationMatchResult(
                    bank.rowNumber(),
                    ReconciliationTier.BATCH_REF_AMOUNT_SUM,
                    98,
                    ReconciliationDisposition.AUTO_MATCHED,
                    "Batch ref/party sum matched cheque amount",
                    group.getFirst(),
                    group,
                    gk,
                    true,
                    "BATCH_SUM",
                    minDate(group),
                    maxDate(group));
            return new BatchDecision(true, result);
        }

        List<List<QboReconCandidate>> subsets = findSumSubsets(group, bank.signedAmount(), 5);
        if (subsets.size() == 1 && subsets.getFirst().size() > 1) {
            List<QboReconCandidate> subset = subsets.getFirst();
            ReconciliationMatchResult result = new ReconciliationMatchResult(
                    bank.rowNumber(),
                    ReconciliationTier.BATCH_REF_AMOUNT_SUM,
                    94,
                    ReconciliationDisposition.AUTO_MATCHED,
                    "Unique candidate subset matched cheque amount",
                    subset.getFirst(),
                    subset,
                    gk,
                    true,
                    "BATCH_SUBSET",
                    minDate(subset),
                    maxDate(subset));
            return new BatchDecision(true, result);
        }
        if (subsets.size() > 1) {
            ReconciliationMatchResult result = new ReconciliationMatchResult(
                    bank.rowNumber(),
                    ReconciliationTier.BATCH_REF_EXACT,
                    80,
                    ReconciliationDisposition.NEEDS_REVIEW,
                    "Ambiguous batch subsets found: " + subsets.size(),
                    subsets.getFirst().getFirst(),
                    subsets.getFirst(),
                    gk,
                    true,
                    "AMBIGUOUS_SUBSET",
                    minDate(subsets.getFirst()),
                    maxDate(subsets.getFirst()));
            return new BatchDecision(false, result);
        }

        if (group.size() > 1) {
            ReconciliationMatchResult result = new ReconciliationMatchResult(
                    bank.rowNumber(),
                    ReconciliationTier.BATCH_REF_EXACT,
                    75,
                    ReconciliationDisposition.NEEDS_REVIEW,
                    "Reference/party group found but amount sum differs",
                    group.getFirst(),
                    group,
                    gk,
                    true,
                    "GROUP_REVIEW",
                    minDate(group),
                    maxDate(group));
            return new BatchDecision(false, result);
        }

        return null;
    }

    private MatchDecision scoreBest(BankStatementRow bank, List<QboReconCandidate> candidates, int dateWindowDays) {
        return candidates.stream()
                .map(candidate -> score(bank, candidate, dateWindowDays))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt((MatchDecision decision) -> decision.confidence)
                        .thenComparingInt(decision -> -Math.abs(daysBetween(bank.txnDate(), decision.candidate.txnDate())))
                        .thenComparingInt(decision -> exactRefOverlap(bank.reference(), decision.candidate.reference()) ? 1 : 0)
                        .thenComparing(decision -> decision.candidate.txnId()))
                .orElse(null);
    }

    private MatchDecision score(BankStatementRow bank, QboReconCandidate candidate, int dateWindowDays) {
        if (bank == null || candidate == null || bank.signedAmount() == null || candidate.signedAmount() == null) {
            return null;
        }
        if (bank.signedAmount().compareTo(candidate.signedAmount()) != 0) {
            return null;
        }
        int days = Math.abs(daysBetween(bank.txnDate(), candidate.txnDate()));
        if (exactRefOverlap(bank.reference(), candidate.reference()) && days == 0) {
            return new MatchDecision(candidate, ReconciliationTier.TIER1, 100, "Exact reference + amount + date");
        }
        if (days <= dateWindowDays) {
            int confidence = Math.max(70, 90 - days * 5);
            if (exactRefOverlap(bank.reference(), candidate.reference())) {
                confidence = Math.min(95, confidence + 5);
            }
            return new MatchDecision(candidate, ReconciliationTier.TIER2, confidence, "Amount matched within date window");
        }
        int fuzzy = tokenOverlap(bank.memo() + " " + bank.counterparty() + " " + bank.reference(),
                candidate.party() + " " + candidate.reference());
        if (fuzzy >= 40) {
            return new MatchDecision(candidate, ReconciliationTier.TIER3, Math.min(89, 50 + fuzzy / 2), "Amount matched with fuzzy text overlap");
        }
        return null;
    }

    private List<List<QboReconCandidate>> findSumSubsets(List<QboReconCandidate> items, BigDecimal target, int maxSize) {
        List<List<QboReconCandidate>> found = new ArrayList<>();
        backtrack(items, target, 0, new ArrayList<>(), BigDecimal.ZERO, found, maxSize);
        found.sort(Comparator
                .comparingInt((List<QboReconCandidate> list) -> list.size())
                .thenComparing(list -> list.stream().map(QboReconCandidate::txnId).sorted().collect(Collectors.joining("|"))));
        return found;
    }

    private void backtrack(List<QboReconCandidate> items,
                           BigDecimal target,
                           int index,
                           List<QboReconCandidate> current,
                           BigDecimal sum,
                           List<List<QboReconCandidate>> found,
                           int maxSize) {
        if (sum.compareTo(target) == 0 && !current.isEmpty()) {
            found.add(List.copyOf(current));
            return;
        }
        if (index >= items.size() || current.size() >= maxSize) {
            return;
        }
        for (int i = index; i < items.size(); i++) {
            QboReconCandidate candidate = items.get(i);
            BigDecimal next = sum.add(candidate.signedAmount());
            if (next.abs().compareTo(target.abs().multiply(new BigDecimal("1.25"))) > 0) {
                continue;
            }
            current.add(candidate);
            backtrack(items, target, i + 1, current, next, found, maxSize);
            current.remove(current.size() - 1);
        }
    }

    private boolean exactRefOverlap(String left, String right) {
        return !StringUtils.isBlank(left) && !StringUtils.isBlank(right)
                && normalizeToken(left).equals(normalizeToken(right));
    }

    private int tokenOverlap(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int common = 0;
        for (String token : a) {
            if (b.contains(token)) {
                common++;
            }
        }
        return (int) Math.round((common * 100.0) / Math.max(a.size(), b.size()));
    }

    private Set<String> tokens(String input) {
        if (StringUtils.isBlank(input)) {
            return Set.of();
        }
        String[] split = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String item : split) {
            if (item.length() >= 2) {
                tokens.add(item);
            }
        }
        return tokens;
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").trim();
    }

    private int daysBetween(LocalDate left, LocalDate right) {
        if (left == null || right == null) {
            return 999;
        }
        return (int) Math.abs(left.toEpochDay() - right.toEpochDay());
    }

    private String read(ParsedCsvRow row, String header) {
        if (row == null || StringUtils.isBlank(header)) {
            return "";
        }
        return row.values().getOrDefault(header, "");
    }

    private LocalDate parseDate(String input) {
        if (StringUtils.isBlank(input)) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(input.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(input.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private BigDecimal parseAmount(String input) {
        if (StringUtils.isBlank(input)) {
            return null;
        }
        String normalized = input.replace(",", "").replace("$", "").trim();
        if (normalized.startsWith("(") && normalized.endsWith(")")) {
            normalized = "-" + normalized.substring(1, normalized.length() - 1);
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ReconciliationDisposition parseDisposition(String value) {
        if (StringUtils.isBlank(value)) {
            return ReconciliationDisposition.SKIPPED;
        }
        try {
            return ReconciliationDisposition.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return ReconciliationDisposition.SKIPPED;
        }
    }

    private ReconciliationTier parseTier(String value) {
        if (StringUtils.isBlank(value)) {
            return ReconciliationTier.NONE;
        }
        try {
            return ReconciliationTier.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return ReconciliationTier.NONE;
        }
    }

    private List<QboReconCandidate> resolveRowCandidates(ReconciliationSessionRowEntity row) {
        if (!StringUtils.isBlank(row.getCandidateTxnIds())) {
            Map<String, QboReconCandidate> byId = quickLookup(row);
            List<QboReconCandidate> resolved = new ArrayList<>();
            for (String id : row.getCandidateTxnIds().split("\\|")) {
                QboReconCandidate candidate = byId.get(id);
                if (candidate != null) {
                    resolved.add(candidate);
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        if (!StringUtils.isBlank(row.getQboTxnId())) {
            return List.of(new QboReconCandidate(
                    row.getQboTxnId(),
                    row.getQboSyncToken(),
                    row.getQboEntityType(),
                    row.getQboTxnDate(),
                    row.getQboAmount(),
                    row.getQboReference(),
                    row.getQboParty(),
                    null));
        }
        return List.of();
    }

    private Map<String, QboReconCandidate> quickLookup(ReconciliationSessionRowEntity row) {
        LocalDate from = row.getGroupWindowStart() == null ? LocalDate.now().minusDays(20) : row.getGroupWindowStart();
        LocalDate to = row.getGroupWindowEnd() == null ? LocalDate.now().plusDays(20) : row.getGroupWindowEnd();
        String realmId = connectionService.getActiveConnection().getRealmId();
        List<QboReconCandidate> candidates = quickBooksGateway.listReconciliationCandidates(realmId, from, to);
        return candidates.stream().collect(Collectors.toMap(QboReconCandidate::txnId, item -> item, (a, b) -> a));
    }

    private String groupKey(String reference, String party) {
        return normalizeToken(reference) + "|" + normalizeToken(party);
    }

    private LocalDate minDate(List<QboReconCandidate> list) {
        return list.stream().map(QboReconCandidate::txnDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
    }

    private LocalDate maxDate(List<QboReconCandidate> list) {
        return list.stream().map(QboReconCandidate::txnDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }

    private record MatchDecision(QboReconCandidate candidate, ReconciliationTier tier, int confidence, String reason) {
    }

    private record BatchDecision(boolean auto, ReconciliationMatchResult result) {
    }

    public record ReconciliationColumnMapping(
            String txnDateHeader,
            String amountHeader,
            String debitHeader,
            String creditHeader,
            String referenceHeader,
            String memoHeader,
            String counterpartyHeader) {
    }
}
