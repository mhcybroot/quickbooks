# Import Run Live Progress Tracking - Fix Plan

## Problem Statement
When running "Preview & Validate", the UI shows live progress by tracking a specific `previewJobId` and polling for `AppJobSnapshot`. This works correctly.

However, when running "Import" or "Import Ready Rows", the UI falls back to querying by `sourceFileName` instead of tracking a specific run ID. This causes the live progress not to show because:

1. `ImportBackgroundService.enqueueForCurrentCompany()` is a `void` method - it doesn't return the run ID
2. The UI sets `trackingFileName = uploadedFileName` and polls `findLatestRunProgressForFile(entityType, sourceFileName)`
3. `findLatestRunForFile()` queries by `sourceFileName` ordered by `createdAt DESC` 
4. But this query may not find the newly started run immediately since it's async and the run entity may not be persisted yet
5. Even if it finds it, the progress values might not update properly since the run is just starting

## Current Flow (Broken)
```
UI: importPreview(ImportExecutionMode mode)
  → backgroundService.enqueueForCurrentCompany(...) // returns void
  → trackingFileName = uploadedFileName
  → poll: refreshBackgroundProgress()
    → importProgressService.findLatestRunProgressForFile(entityType, sourceFileName)
    → importHistoryService.findLatestRunForFile(entityType, sourceFileName)
    → importRunRepository.findTopByEntityTypeAndSourceFileNameAndCompanyIdOrderByCreatedAtDesc(...)
```

## Desired Flow (Like Preview)
```
UI: importPreview(ImportExecutionMode mode)
  → backgroundService.enqueueForCurrentCompany(...) // returns runId (or tracks it)
  → set runId for tracking (like previewJobId)
  → poll: refreshRunProgress(runId)  // <-- Track by runId, not by filename
    → importProgressService.findRunProgress(runId)
```

## Solution Options

### Option A: Return Run ID from enqueue (Recommended)
Modify `ImportBackgroundService` to capture and return the run ID when the async task starts. But since it's `@Async`, we can't directly return the result.

**Approach**: 
- Store the `runId` in a `ConcurrentHashMap` keyed by some correlation ID
- Or have the service method return immediately with the `runId` before the async execution starts
- The UI can then track by `runId` instead of `sourceFileName`

### Option B: Query by filename with retry/wait logic
Add logic in `findLatestRunProgressForFile()` to wait briefly if no run is found yet. But this is hacky.

### Option C: Use JobService approach
Similar to how preview jobs work, treat import runs as "jobs" that can be tracked via `AppJobService`.

## Recommended Fix: Option A - Return Run ID Before Async

The cleanest approach is to:
1. Modify `ImportWorkflowFacade.execute()` to return run ID or create the run entity before async execution
2. Change `ImportBackgroundService.enqueueForCurrentCompany()` to return the run ID
3. Update UI to track by `runId` instead of `trackingFileName`

But since `execute()` is called inside `@Async` method, we need to restructure:

1. Create run entity BEFORE calling async
2. Pass run ID to async method
3. Async method updates the existing run
4. UI gets run ID immediately and can track it

## Implementation Tasks

- [ ] 1. Modify `ImportBackgroundService.enqueueForCurrentCompany()` to return `Long runId`
- [ ] 2. Update `ImportWorkflowFacade` to create run entity before async execution
- [ ] 3. Modify UI to use `runId` tracking instead of `trackingFileName`
- [ ] 4. Change `refreshBackgroundProgress()` to use `findRunProgress(Long runId)` instead of `findLatestRunProgressForFile()`
- [ ] 5. Add `runId` field to Import View UI class
- [ ] 6. Update all entity type import views (Invoice, Payment, Expense, SalesReceipt, Bill, BillPayment)

## Affected Files
- `src/main/java/com/example/quickbooksimporter/service/ImportBackgroundService.java` - Add return type
- `src/main/java/com/example/quickbooksimporter/service/ImportWorkflowFacade.java` - Restructure to pre-create run
- `src/main/java/com/example/quickbooksimporter/ui/InvoiceImportView.java` - Track by runId
- `src/main/java/com/example/quickbooksimporter/ui/PaymentImportView.java` - Track by runId  
- `src/main/java/com/example/quickbooksimporter/ui/ExpenseImportView.java` - Track by runId
- `src/main/java/com/example/quickbooksimporter/ui/SalesReceiptImportView.java` - Track by runId
- `src/main/java/com/example/quickbooksimporter/ui/BillImportView.java` - Track by runId
- `src/main/java/com/example/quickbooksimporter/ui/BillPaymentImportView.java` - Track by runId
- `src/main/java/com/example/quickbooksimporter/service/ImportProgressService.java` - Already has `findRunProgress(Long runId)`