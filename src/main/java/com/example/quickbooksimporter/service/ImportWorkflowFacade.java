package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.BillImportPreview;
import com.example.quickbooksimporter.domain.BillImportPreviewRow;
import com.example.quickbooksimporter.domain.BillPaymentImportPreview;
import com.example.quickbooksimporter.domain.BillPaymentImportPreviewRow;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ExpenseImportPreview;
import com.example.quickbooksimporter.domain.ExpenseImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportPreview;
import com.example.quickbooksimporter.domain.ImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedBillField;
import com.example.quickbooksimporter.domain.NormalizedBillPaymentField;
import com.example.quickbooksimporter.domain.NormalizedExpenseField;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.domain.NormalizedPaymentField;
import com.example.quickbooksimporter.domain.NormalizedSalesReceiptField;
import com.example.quickbooksimporter.domain.PaymentImportPreview;
import com.example.quickbooksimporter.domain.PaymentImportPreviewRow;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreview;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreviewRow;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ImportWorkflowFacade {

    private final InvoiceCsvParser parser;
    private final CsvMappingProfileService invoiceProfiles;
    private final PaymentMappingProfileService paymentProfiles;
    private final ExpenseMappingProfileService expenseProfiles;
    private final SalesReceiptMappingProfileService salesReceiptProfiles;
    private final BillMappingProfileService billProfiles;
    private final BillPaymentMappingProfileService billPaymentProfiles;
    private final InvoiceImportService invoiceImportService;
    private final PaymentImportService paymentImportService;
    private final ExpenseImportService expenseImportService;
    private final SalesReceiptImportService salesReceiptImportService;
    private final BillImportService billImportService;
    private final BillPaymentImportService billPaymentImportService;
    private final ImportRunRepository importRunRepository;
    private final InvoiceGroupingPreferenceService invoiceGroupingPreferenceService;

    public ImportWorkflowFacade(InvoiceCsvParser parser,
                                CsvMappingProfileService invoiceProfiles,
                                PaymentMappingProfileService paymentProfiles,
                                ExpenseMappingProfileService expenseProfiles,
                                SalesReceiptMappingProfileService salesReceiptProfiles,
                                BillMappingProfileService billProfiles,
                                BillPaymentMappingProfileService billPaymentProfiles,
                                InvoiceImportService invoiceImportService,
                                PaymentImportService paymentImportService,
                                ExpenseImportService expenseImportService,
                                SalesReceiptImportService salesReceiptImportService,
                                BillImportService billImportService,
                                BillPaymentImportService billPaymentImportService,
                                ImportRunRepository importRunRepository,
                                InvoiceGroupingPreferenceService invoiceGroupingPreferenceService) {
        this.parser = parser;
        this.invoiceProfiles = invoiceProfiles;
        this.paymentProfiles = paymentProfiles;
        this.expenseProfiles = expenseProfiles;
        this.salesReceiptProfiles = salesReceiptProfiles;
        this.billProfiles = billProfiles;
        this.billPaymentProfiles = billPaymentProfiles;
        this.invoiceImportService = invoiceImportService;
        this.paymentImportService = paymentImportService;
        this.expenseImportService = expenseImportService;
        this.salesReceiptImportService = salesReceiptImportService;
        this.billImportService = billImportService;
        this.billPaymentImportService = billPaymentImportService;
        this.importRunRepository = importRunRepository;
        this.invoiceGroupingPreferenceService = invoiceGroupingPreferenceService;
    }

    public ParsedCsvDocument parse(byte[] bytes) {
        return parser.parse(new ByteArrayInputStream(bytes));
    }

    public List<MappingProfileSummary> listProfiles(EntityType entityType) {
        return switch (entityType) {
            case INVOICE -> invoiceProfiles.listProfiles();
            case PAYMENT -> paymentProfiles.listProfiles();
            case EXPENSE -> expenseProfiles.listProfiles();
            case SALES_RECEIPT -> salesReceiptProfiles.listProfiles();
            case BILL -> billProfiles.listProfiles();
            case BILL_PAYMENT -> billPaymentProfiles.listProfiles();
        };
    }

    public Optional<MappingProfileSummary> lastUsedProfile(EntityType entityType) {
        Optional<String> recentName = importRunRepository
                .findTopByEntityTypeAndMappingProfileNameIsNotNullOrderByCreatedAtDesc(entityType)
                .map(ImportRunEntity -> ImportRunEntity.getMappingProfileName());
        if (recentName.isEmpty()) {
            return Optional.empty();
        }
        return listProfiles(entityType).stream()
                .filter(profile -> Objects.equals(profile.name(), recentName.get()))
                .findFirst();
    }

    public Map<String, String> loadProfile(EntityType entityType, Long profileId) {
        return switch (entityType) {
            case INVOICE -> stringify(invoiceProfiles.loadProfile(profileId));
            case PAYMENT -> stringify(paymentProfiles.loadProfile(profileId));
            case EXPENSE -> stringify(expenseProfiles.loadProfile(profileId));
            case SALES_RECEIPT -> stringify(salesReceiptProfiles.loadProfile(profileId));
            case BILL -> stringify(billProfiles.loadProfile(profileId));
            case BILL_PAYMENT -> stringify(billPaymentProfiles.loadProfile(profileId));
        };
    }

    public Map<String, String> defaultMapping(EntityType entityType, List<String> headers) {
        return switch (entityType) {
            case INVOICE -> stringify(invoiceProfiles.defaultInvoiceMapping(headers));
            case PAYMENT -> stringify(paymentProfiles.defaultPaymentMapping(headers));
            case EXPENSE -> stringify(expenseProfiles.defaultExpenseMapping(headers));
            case SALES_RECEIPT -> stringify(salesReceiptProfiles.defaultMapping(headers));
            case BILL -> stringify(billProfiles.defaultMapping(headers));
            case BILL_PAYMENT -> stringify(billPaymentProfiles.defaultMapping(headers));
        };
    }

    public ImportPreviewSummary preview(EntityType entityType,
                                        String fileName,
                                        byte[] bytes,
                                        Long profileId,
                                        Map<String, String> mappingOverride) {
        return preview(entityType, fileName, bytes, profileId, mappingOverride, ImportPreviewOptions.defaults());
    }

    public ImportPreviewSummary preview(EntityType entityType,
                                        String fileName,
                                        byte[] bytes,
                                        Long profileId,
                                        Map<String, String> mappingOverride,
                                        ImportPreviewOptions options) {
        ParsedCsvDocument document = parse(bytes);
        Map<String, String> mapping = profileId == null
                ? defaultMapping(entityType, document.headers())
                : loadProfile(entityType, profileId);
        if (mappingOverride != null && !mappingOverride.isEmpty()) {
            mapping.putAll(mappingOverride);
        }
        String suggestedProfile = lastUsedProfile(entityType).map(MappingProfileSummary::name).orElse(null);

        return switch (entityType) {
            case INVOICE -> summarizeInvoicePreview(
                    invoiceImportService.preview(
                            fileName,
                            bytes,
                            toInvoiceMapping(mapping),
                            options.invoiceGroupingEnabled() == null
                                    ? invoiceGroupingPreferenceService.isGroupingEnabled()
                                    : options.invoiceGroupingEnabled()),
                    suggestedProfile);
            case PAYMENT -> summarizePaymentPreview(
                    paymentImportService.preview(
                            fileName,
                            bytes,
                            toPaymentMapping(mapping),
                            options.draftInvoiceRefs() == null ? Map.of() : options.draftInvoiceRefs(),
                            DateFormatOption.AUTO),
                    suggestedProfile);
            case EXPENSE -> summarizeExpensePreview(
                    expenseImportService.preview(fileName, bytes, toExpenseMapping(mapping)),
                    suggestedProfile);
            case SALES_RECEIPT -> summarizeSalesReceiptPreview(
                    salesReceiptImportService.preview(fileName, bytes, toSalesReceiptMapping(mapping)),
                    suggestedProfile);
            case BILL -> summarizeBillPreview(
                    billImportService.preview(fileName, bytes, toBillMapping(mapping)),
                    suggestedProfile);
            case BILL_PAYMENT -> summarizeBillPaymentPreview(
                    billPaymentImportService.preview(fileName, bytes, toBillPaymentMapping(mapping)),
                    suggestedProfile);
        };
    }

    public ImportExecutionResult execute(EntityType entityType,
                                         String fileName,
                                         String mappingProfileName,
                                         Object rawPreview,
                                         ImportExecutionOptions options) {
        ImportExecutionOptions executionOptions = options == null ? ImportExecutionOptions.standalone() : options;
        return switch (entityType) {
            case INVOICE -> invoiceImportService.execute(fileName, mappingProfileName, (ImportPreview) rawPreview, executionOptions);
            case PAYMENT -> paymentImportService.execute(fileName, mappingProfileName, (PaymentImportPreview) rawPreview, executionOptions);
            case EXPENSE -> expenseImportService.execute(fileName, mappingProfileName, (ExpenseImportPreview) rawPreview, executionOptions);
            case SALES_RECEIPT -> salesReceiptImportService.execute(fileName, mappingProfileName, (SalesReceiptImportPreview) rawPreview, executionOptions);
            case BILL -> billImportService.execute(fileName, mappingProfileName, (BillImportPreview) rawPreview, executionOptions);
            case BILL_PAYMENT -> billPaymentImportService.execute(fileName, mappingProfileName, (BillPaymentImportPreview) rawPreview, executionOptions);
        };
    }

    public Set<String> producedIdentifiers(EntityType entityType, Object rawPreview) {
        return switch (entityType) {
            case INVOICE -> ((ImportPreview) rawPreview).rows().stream()
                    .map(ImportPreviewRow::invoiceNo)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            case BILL -> ((BillImportPreview) rawPreview).rows().stream()
                    .map(BillImportPreviewRow::billNo)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            default -> Set.of();
        };
    }

    public Map<String, QuickBooksInvoiceRef> draftInvoiceRefs(EntityType entityType, Object rawPreview) {
        if (entityType != EntityType.INVOICE) {
            return Map.of();
        }
        ImportPreview preview = (ImportPreview) rawPreview;
        if (!preview.groupingEnabled()) {
            return Map.of();
        }
        Map<String, QuickBooksInvoiceRef> refs = new java.util.LinkedHashMap<>();
        preview.validations().stream()
                .filter(validation -> validation.status() == ImportRowStatus.READY && validation.invoice() != null)
                .forEach(validation -> refs.put(
                        validation.invoice().invoiceNo(),
                        new QuickBooksInvoiceRef(
                                null,
                                validation.invoice().invoiceNo(),
                                null,
                                validation.invoice().customer(),
                                validation.invoice().lines().stream()
                                        .map(line -> line.amount() == null ? java.math.BigDecimal.ZERO : line.amount())
                                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))));
        return refs;
    }

    public Set<String> requiredParentIdentifiers(EntityType entityType, Object rawPreview) {
        return switch (entityType) {
            case PAYMENT -> ((PaymentImportPreview) rawPreview).rows().stream()
                    .map(PaymentImportPreviewRow::invoiceNo)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            case BILL_PAYMENT -> ((BillPaymentImportPreview) rawPreview).rows().stream()
                    .map(BillPaymentImportPreviewRow::billNo)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            default -> Set.of();
        };
    }

    private ImportPreviewSummary summarizeInvoicePreview(ImportPreview preview, String suggestedProfile) {
        int ready = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        int invalid = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        int duplicates = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.DUPLICATE).count();
        List<String> warnings = new ArrayList<>();
        if (invalid > 0) {
            warnings.add("Fix invalid invoice rows before import.");
        }
        return new ImportPreviewSummary(EntityType.INVOICE, preview.sourceFileName(), preview.headers(),
                preview.rows().size(), ready, invalid, duplicates, preview.exportCsv(), suggestedProfile, warnings, preview);
    }

    private ImportPreviewSummary summarizePaymentPreview(PaymentImportPreview preview, String suggestedProfile) {
        int ready = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        int invalid = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        int duplicates = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.DUPLICATE).count();
        List<String> warnings = new ArrayList<>();
        if (invalid > 0) {
            warnings.add("Payments with invalid invoice references will be blocked.");
        }
        return new ImportPreviewSummary(EntityType.PAYMENT, preview.sourceFileName(), preview.headers(),
                preview.rows().size(), ready, invalid, duplicates, null, suggestedProfile, warnings, preview);
    }

    private ImportPreviewSummary summarizeExpensePreview(ExpenseImportPreview preview, String suggestedProfile) {
        int ready = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        int invalid = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        int duplicates = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.DUPLICATE).count();
        return new ImportPreviewSummary(EntityType.EXPENSE, preview.sourceFileName(), preview.headers(),
                preview.rows().size(), ready, invalid, duplicates, null, suggestedProfile, List.of(), preview);
    }

    private ImportPreviewSummary summarizeSalesReceiptPreview(SalesReceiptImportPreview preview, String suggestedProfile) {
        int ready = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        int invalid = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        int duplicates = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.DUPLICATE).count();
        return new ImportPreviewSummary(EntityType.SALES_RECEIPT, preview.sourceFileName(), preview.headers(),
                preview.rows().size(), ready, invalid, duplicates, null, suggestedProfile, List.of(), preview);
    }

    private ImportPreviewSummary summarizeBillPreview(BillImportPreview preview, String suggestedProfile) {
        int ready = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        int invalid = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        int duplicates = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.DUPLICATE).count();
        List<String> warnings = new ArrayList<>();
        if (invalid > 0) {
            warnings.add("Bills with line grouping problems will be blocked.");
        }
        return new ImportPreviewSummary(EntityType.BILL, preview.sourceFileName(), preview.headers(),
                preview.rows().size(), ready, invalid, duplicates, null, suggestedProfile, warnings, preview);
    }

    private ImportPreviewSummary summarizeBillPaymentPreview(BillPaymentImportPreview preview, String suggestedProfile) {
        int ready = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        int invalid = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        int duplicates = (int) preview.rows().stream().filter(row -> row.status() == ImportRowStatus.DUPLICATE).count();
        List<String> warnings = new ArrayList<>();
        if (invalid > 0) {
            warnings.add("Bill payments with missing bill references will be blocked.");
        }
        return new ImportPreviewSummary(EntityType.BILL_PAYMENT, preview.sourceFileName(), preview.headers(),
                preview.rows().size(), ready, invalid, duplicates, null, suggestedProfile, warnings, preview);
    }

    private Map<String, String> stringify(Map<?, String> typedMapping) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        typedMapping.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<NormalizedInvoiceField, String> toInvoiceMapping(Map<String, String> mapping) {
        Map<NormalizedInvoiceField, String> result = new EnumMap<>(NormalizedInvoiceField.class);
        mapping.forEach((key, value) -> result.put(NormalizedInvoiceField.valueOf(key), value));
        return result;
    }

    private Map<NormalizedPaymentField, String> toPaymentMapping(Map<String, String> mapping) {
        Map<NormalizedPaymentField, String> result = new EnumMap<>(NormalizedPaymentField.class);
        mapping.forEach((key, value) -> result.put(NormalizedPaymentField.valueOf(key), value));
        return result;
    }

    private Map<NormalizedExpenseField, String> toExpenseMapping(Map<String, String> mapping) {
        Map<NormalizedExpenseField, String> result = new EnumMap<>(NormalizedExpenseField.class);
        mapping.forEach((key, value) -> result.put(NormalizedExpenseField.valueOf(key), value));
        return result;
    }

    private Map<NormalizedSalesReceiptField, String> toSalesReceiptMapping(Map<String, String> mapping) {
        Map<NormalizedSalesReceiptField, String> result = new EnumMap<>(NormalizedSalesReceiptField.class);
        mapping.forEach((key, value) -> result.put(NormalizedSalesReceiptField.valueOf(key), value));
        return result;
    }

    private Map<NormalizedBillField, String> toBillMapping(Map<String, String> mapping) {
        Map<NormalizedBillField, String> result = new EnumMap<>(NormalizedBillField.class);
        mapping.forEach((key, value) -> result.put(NormalizedBillField.valueOf(key), value));
        return result;
    }

    private Map<NormalizedBillPaymentField, String> toBillPaymentMapping(Map<String, String> mapping) {
        Map<NormalizedBillPaymentField, String> result = new EnumMap<>(NormalizedBillPaymentField.class);
        mapping.forEach((key, value) -> result.put(NormalizedBillPaymentField.valueOf(key), value));
        return result;
    }
}
