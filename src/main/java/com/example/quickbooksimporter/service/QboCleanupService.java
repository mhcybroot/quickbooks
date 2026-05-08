package com.example.quickbooksimporter.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QboCleanupService {
    private static final Logger log = LoggerFactory.getLogger(QboCleanupService.class);

    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway quickBooksGateway;

    public QboCleanupService(QuickBooksConnectionService connectionService, QuickBooksGateway quickBooksGateway) {
        this.connectionService = connectionService;
        this.quickBooksGateway = quickBooksGateway;
    }

    public List<QboTransactionRow> list(QboCleanupEntityType type, QboCleanupFilter filter, boolean includeAllCompanyData) {
        String realmId = connectionService.getActiveConnection().getRealmId();
        QboCleanupFilter effective = filter == null ? QboCleanupFilter.defaults() : filter;
        log.debug("Cleanup list request: realmId={}, type={}, includeAll={}, filter={}",
                realmId, type, includeAllCompanyData, effective);
        if (!includeAllCompanyData) {
            List<QboTransactionRow> rows = applyLocalFilters(quickBooksGateway.listTransactions(realmId, type, effective, 1), effective);
            log.debug("Cleanup list result: realmId={}, type={}, count={}", realmId, type, rows.size());
            return rows;
        }
        List<QboTransactionRow> all = new ArrayList<>();
        int start = 1;
        while (true) {
            List<QboTransactionRow> page = quickBooksGateway.listTransactions(realmId, type, effective, start);
            if (page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < effective.pageSize()) {
                break;
            }
            start += effective.pageSize();
        }
        List<QboTransactionRow> rows = applyLocalFilters(all, effective);
        log.debug("Cleanup list result: realmId={}, type={}, count={}", realmId, type, rows.size());
        return rows;
    }

    public CleanupActionResponse delete(QboCleanupEntityType type, List<QboTransactionRow> transactions) {
        return runAction(type, transactions, true);
    }

    public CleanupActionResponse voidTransactions(QboCleanupEntityType type, List<QboTransactionRow> transactions) {
        return runAction(type, transactions, false);
    }

    private CleanupActionResponse runAction(QboCleanupEntityType type, List<QboTransactionRow> transactions, boolean delete) {
        if (transactions == null || transactions.isEmpty()) {
            return new CleanupActionResponse(List.of(), Map.of());
        }
        String action = delete ? "DELETE" : "VOID";
        String realmId = connectionService.getActiveConnection().getRealmId();
        log.info("Cleanup action start: realmId={}, type={}, action={}, requested={}", realmId, type, action, transactions.size());
        Map<String, List<QboDependencyBlocker>> blockers = quickBooksGateway.findDependencyBlockers(realmId, type, transactions);
        List<QboCleanupResult> results = new ArrayList<>();
        for (QboTransactionRow transaction : transactions) {
            if (blockers.containsKey(transaction.id())) {
                String reason = blockers.get(transaction.id()).stream().map(QboDependencyBlocker::reason).findFirst()
                        .orElse("Blocked by dependencies");
                results.add(new QboCleanupResult(transaction.id(), transaction.externalNumber(),
                        delete ? "DELETE" : "VOID", false, reason, null));
                log.warn("Cleanup blocked: realmId={}, type={}, action={}, id={}, docRef={}, reason={}",
                        realmId, type, action, transaction.id(), transaction.externalNumber(), reason);
                continue;
            }
            QboCleanupResult result = delete
                    ? quickBooksGateway.deleteTransaction(realmId, type, transaction)
                    : quickBooksGateway.voidTransaction(realmId, type, transaction);
            results.add(result);
            if (result.success()) {
                log.info("Cleanup success: realmId={}, type={}, action={}, id={}, docRef={}",
                        realmId, type, action, result.recordId(), result.externalNumber());
            } else {
                log.error("Cleanup failed: realmId={}, type={}, action={}, id={}, docRef={}, intuit_tid={}, message={}",
                        realmId, type, action, result.recordId(), result.externalNumber(), result.intuitTid(), result.message());
            }
        }
        long failures = results.stream().filter(result -> !result.success()).count();
        log.info("Cleanup action completed: realmId={}, type={}, action={}, requested={}, failures={}",
                realmId, type, action, transactions.size(), failures);
        return new CleanupActionResponse(results, blockers);
    }

    public record CleanupActionResponse(
            List<QboCleanupResult> results,
            Map<String, List<QboDependencyBlocker>> blockers) {
    }

    private List<QboTransactionRow> applyLocalFilters(List<QboTransactionRow> rows, QboCleanupFilter filter) {
        if (filter == null || filter.partyContains() == null || filter.partyContains().isBlank()) {
            return rows;
        }
        String needle = filter.partyContains().trim().toLowerCase();
        return rows.stream()
                .filter(row -> row.partyName() != null && row.partyName().toLowerCase().contains(needle))
                .toList();
    }
}
