package com.example.quickbooksimporter.persistence;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "import_row_result")
public class ImportRowResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "import_run_id")
    private ImportRunEntity importRun;

    @Column(nullable = false)
    private int rowNumber;

    private String sourceIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportRowStatus status;

    @Column(columnDefinition = "text")
    private String message;

    @Column(columnDefinition = "text")
    private String rawData;

    @Column(columnDefinition = "text")
    private String normalizedData;

    private String createdEntityId;

    public Long getId() {
        return id;
    }

    public ImportRunEntity getImportRun() {
        return importRun;
    }

    public void setImportRun(ImportRunEntity importRun) {
        this.importRun = importRun;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getSourceIdentifier() {
        return sourceIdentifier;
    }

    public void setSourceIdentifier(String sourceIdentifier) {
        this.sourceIdentifier = sourceIdentifier;
    }

    public ImportRowStatus getStatus() {
        return status;
    }

    public void setStatus(ImportRowStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public String getNormalizedData() {
        return normalizedData;
    }

    public void setNormalizedData(String normalizedData) {
        this.normalizedData = normalizedData;
    }

    public String getCreatedEntityId() {
        return createdEntityId;
    }

    public void setCreatedEntityId(String createdEntityId) {
        this.createdEntityId = createdEntityId;
    }
}
