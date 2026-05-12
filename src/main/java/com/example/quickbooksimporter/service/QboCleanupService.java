package com.example.quickbooksimporter.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
        boolean fetchAllPages = true;
        log.debug("Cleanup list request: realmId={}, type={}, includeAll={}, filter={}",
                realmId, type, includeAllCompanyData, effective);
        List<QboTransactionRow> all = new ArrayList<>();
        int start = 1;
        while (true) {
            List<QboTransactionRow> page = quickBooksGateway.listTransactions(realmId, type, effective, start);
            log.debug("Cleanup list page: realmId={}, type={}, start={}, fetched={}", realmId, type, start, page.size());
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
        rows = applyLocalSort(rows, effective);
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
                        "PARENT", null, delete ? "DELETE" : "VOID", false, reason, null));
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

    public QboCleanupDryRunPlan prepareRecoveryPlan(QboCleanupEntityType rootType, List<QboTransactionRow> roots) {
        if (roots == null || roots.isEmpty()) {
            return new QboCleanupDryRunPlan(0, 0, 0, List.of(), Map.of());
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        List<QboCleanupExecutionStep> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<QboTransactionRow> linkedOnly = new ArrayList<>();
        for (QboTransactionRow root : roots) {
            resolveDependenciesRecursively(realmId, rootType, root, ordered, linkedOnly, visited);
        }
        for (QboTransactionRow root : roots) {
            ordered.add(new QboCleanupExecutionStep(root, "DELETE"));
        }
        Map<QboCleanupEntityType, Long> byType = linkedOnly.stream()
                .collect(Collectors.groupingBy(QboTransactionRow::entityType, LinkedHashMap::new, Collectors.counting()));
        return new QboCleanupDryRunPlan(
                roots.size(),
                linkedOnly.size(),
                ordered.size(),
                ordered,
                byType);
    }

    public QboCleanupRecoveryResult executeRecoveryPlan(QboCleanupDryRunPlan plan, boolean allowVoidFallback) {
        if (plan == null || plan.orderedSteps().isEmpty()) {
            return new QboCleanupRecoveryResult(List.of(), false);
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        List<QboCleanupResult> results = new ArrayList<>();
        boolean usedVoidFallback = false;
        for (QboCleanupExecutionStep step : plan.orderedSteps()) {
            QboTransactionRow current = quickBooksGateway.findTransactionById(realmId, step.transaction().entityType(), step.transaction().id());
            if (current == null) {
                results.add(new QboCleanupResult(
                        step.transaction().id(),
                        step.transaction().externalNumber(),
                        "LINKED",
                        null,
                        step.action(),
                        true,
                        "Record already removed",
                        null));
                continue;
            }
            QboCleanupResult result = "VOID".equals(step.action())
                    ? quickBooksGateway.voidTransaction(realmId, current.entityType(), current)
                    : quickBooksGateway.deleteTransaction(realmId, current.entityType(), current);
            String source = isRootStep(plan, step) ? "PARENT" : "LINKED";
            QboCleanupResult normalized = new QboCleanupResult(
                    result.recordId(),
                    result.externalNumber(),
                    source,
                    source.equals("LINKED") ? findParentExternal(plan, step) : null,
                    result.action(),
                    result.success(),
                    result.message(),
                    result.intuitTid());
            if (!normalized.success() && allowVoidFallback && "DELETE".equals(normalized.action()) && current.entityType().voidSupported()) {
                QboCleanupResult fallback = quickBooksGateway.voidTransaction(realmId, current.entityType(), current);
                normalized = new QboCleanupResult(
                        fallback.recordId(),
                        fallback.externalNumber(),
                        source,
                        source.equals("LINKED") ? findParentExternal(plan, step) : null,
                        fallback.action(),
                        fallback.success(),
                        fallback.success() ? "Delete failed; void fallback succeeded." : fallback.message(),
                        fallback.intuitTid());
                usedVoidFallback = true;
            }
            results.add(normalized);
        }
        return new QboCleanupRecoveryResult(results, usedVoidFallback);
    }

    public record CleanupActionResponse(
            List<QboCleanupResult> results,
            Map<String, List<QboDependencyBlocker>> blockers) {
    }

    private void resolveDependenciesRecursively(String realmId,
                                                QboCleanupEntityType type,
                                                QboTransactionRow root,
                                                List<QboCleanupExecutionStep> ordered,
                                                List<QboTransactionRow> linkedOnly,
                                                Set<String> visited) {
        String nodeKey = type + ":" + root.id();
        if (!visited.add(nodeKey)) {
            return;
        }
        List<QboDependencyLink> links = quickBooksGateway.findDirectDependencyLinks(realmId, type, root);
        for (QboDependencyLink link : links) {
            QboTransactionRow child = quickBooksGateway.findTransactionById(realmId, link.childType(), link.childId());
            if (child == null) {
                continue;
            }
            resolveDependenciesRecursively(realmId, link.childType(), child, ordered, linkedOnly, visited);
            linkedOnly.add(child);
            ordered.add(new QboCleanupExecutionStep(child, "DELETE"));
        }
    }

    private boolean isRootStep(QboCleanupDryRunPlan plan, QboCleanupExecutionStep step) {
        int parentStart = Math.max(0, plan.orderedSteps().size() - plan.rootCount());
        return plan.orderedSteps().subList(parentStart, plan.orderedSteps().size()).contains(step);
    }

    private String findParentExternal(QboCleanupDryRunPlan plan, QboCleanupExecutionStep step) {
        int parentStart = Math.max(0, plan.orderedSteps().size() - plan.rootCount());
        if (parentStart >= plan.orderedSteps().size()) {
            return null;
        }
        return plan.orderedSteps().subList(parentStart, plan.orderedSteps().size()).stream()
                .map(parentStep -> parentStep.transaction().externalNumber())
                .findFirst()
                .orElse(null);
    }

    private List<QboTransactionRow> applyLocalFilters(List<QboTransactionRow> rows, QboCleanupFilter filter) {
        if (filter == null) {
            return rows;
        }
        return rows.stream()
                .filter(row -> containsIgnoreCase(row.partyName(), filter.partyContains()))
                .filter(row -> containsIgnoreCase(row.status(), filter.statusContains()))
                .toList();
    }

    private List<QboTransactionRow> applyLocalSort(List<QboTransactionRow> rows, QboCleanupFilter filter) {
        if (rows == null || rows.isEmpty() || filter == null || filter.sortField() == null) {
            return rows;
        }
        Comparator<QboTransactionRow> comparator = switch (filter.sortField()) {
            case TXN_DATE -> Comparator.comparing(QboTransactionRow::txnDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case DOC_NUMBER -> Comparator.comparing(QboTransactionRow::externalNumber, String.CASE_INSENSITIVE_ORDER);
            case PARTY_NAME -> Comparator.comparing(QboTransactionRow::partyName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case TOTAL_AMOUNT -> Comparator.comparing(QboTransactionRow::totalAmount, Comparator.nullsLast(Comparator.naturalOrder()));
            case BALANCE -> Comparator.comparing(QboTransactionRow::balance, Comparator.nullsLast(Comparator.naturalOrder()));
            case QBO_ID -> Comparator.comparing(QboTransactionRow::id, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };
        comparator = comparator.thenComparing(QboTransactionRow::id, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        if (filter.sortDirection() == QboSortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return rows.stream().sorted(comparator).toList();
    }

    private boolean containsIgnoreCase(String source, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        return source != null && source.toLowerCase().contains(needle.trim().toLowerCase());
    }
}
