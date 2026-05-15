# Bill Batch Create Failure - Root Cause Analysis & Fix Plan

## Error Summary
```
QuickBooks batch create failed: realmId=9341456408453714, entityType=Bill, chunkSize=10, status=400 BAD_REQUEST
{"Fault":{"Error":[{"Message":"Request has invalid or unsupported property","Detail":"Property Name:{0} specified is unsupported or invalid","code":"2010"}],"type":"ValidationFault"}}
```

## Investigation Findings

### 1. Code Path
- `BillImportService.importBatch()` (line ~161-175) → `createBillsBatch()`
- `QuickBooksApiGateway.createBillsBatch()` (line 338-339) → `executeBatchCreate()`
- `executeBatchCreate()` calls `buildBillPayload()` for each Bill → sends to QBO Batch endpoint

### 2. Root Cause Identified
In `buildBillPayload()` at line 533, the original code was:
```java
payload.put("DueDate", bill.dueDate() == null ? null : String.valueOf(bill.dueDate()));
```

This explicitly puts a `null` value into the payload map when `dueDate` is null. The QBO Batch API rejects requests containing null field values (it shows "Property Name:{0} specified is unsupported or invalid").

### 3. Fix Applied

Changed line 533 from:
```java
payload.put("DueDate", bill.dueDate() == null ? null : String.valueOf(bill.dueDate()));
```

To:
```java
if (bill.dueDate() != null) {
    payload.put("DueDate", String.valueOf(bill.dueDate()));
}
```

This ensures the `DueDate` field is completely omitted from the JSON payload when null, rather than sending a null value.

### 4. Debug Logging Added
Added debug logging in `executeBatchCreate()` to capture the exact payloads being sent to QBO:
```java
if (log.isDebugEnabled()) {
    log.debug("Batch create payloads for realmId={}, entityType={}, count={}: {}",
            realmId, entityName, payloads.size(), payloads);
}
```

## Verification
- ✅ Code compiles successfully
- ✅ All 61 existing tests pass

## Implementation Tasks Completed
- [x] Add debug logging in `executeBatchCreate()` to log payloads
- [x] Modify `buildBillPayload()` to omit null `DueDate` instead of putting null
- [x] Verify code compiles
- [x] Verify all 61 tests pass