package com.example.quickbooksimporter.persistence;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "import_run")
public class ImportRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportRunStatus status;

    @Column(nullable = false)
    private String sourceFileName;

    private String mappingProfileName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ImportBatchEntity batch;

    private Integer batchOrder;

    private String dependencyGroup;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int validRows;

    @Column(nullable = false)
    private int invalidRows;

    @Column(nullable = false)
    private int duplicateRows;

    @Column(nullable = false)
    private int importedRows;

    @Column(columnDefinition = "text")
    private String exportCsv;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    @OneToMany(mappedBy = "importRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ImportRowResultEntity> rowResults = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public ImportRunStatus getStatus() {
        return status;
    }

    public void setStatus(ImportRunStatus status) {
        this.status = status;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getMappingProfileName() {
        return mappingProfileName;
    }

    public void setMappingProfileName(String mappingProfileName) {
        this.mappingProfileName = mappingProfileName;
    }

    public ImportBatchEntity getBatch() {
        return batch;
    }

    public void setBatch(ImportBatchEntity batch) {
        this.batch = batch;
    }

    public Integer getBatchOrder() {
        return batchOrder;
    }

    public void setBatchOrder(Integer batchOrder) {
        this.batchOrder = batchOrder;
    }

    public String getDependencyGroup() {
        return dependencyGroup;
    }

    public void setDependencyGroup(String dependencyGroup) {
        this.dependencyGroup = dependencyGroup;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public void setValidRows(int validRows) {
        this.validRows = validRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(int invalidRows) {
        this.invalidRows = invalidRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public void setDuplicateRows(int duplicateRows) {
        this.duplicateRows = duplicateRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public void setImportedRows(int importedRows) {
        this.importedRows = importedRows;
    }

    public String getExportCsv() {
        return exportCsv;
    }

    public void setExportCsv(String exportCsv) {
        this.exportCsv = exportCsv;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<ImportRowResultEntity> getRowResults() {
        return rowResults;
    }
}
