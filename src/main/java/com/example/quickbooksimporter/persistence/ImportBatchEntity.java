package com.example.quickbooksimporter.persistence;

import com.example.quickbooksimporter.domain.ImportBatchStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "import_batch")
public class ImportBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String batchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportBatchStatus status;

    @Column(nullable = false)
    private int totalFiles;

    @Column(nullable = false)
    private int validatedFiles;

    @Column(nullable = false)
    private int runnableFiles;

    @Column(nullable = false)
    private int completedFiles;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL)
    private List<ImportRunEntity> runs = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public String getBatchName() {
        return batchName;
    }

    public void setBatchName(String batchName) {
        this.batchName = batchName;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public void setStatus(ImportBatchStatus status) {
        this.status = status;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getValidatedFiles() {
        return validatedFiles;
    }

    public void setValidatedFiles(int validatedFiles) {
        this.validatedFiles = validatedFiles;
    }

    public int getRunnableFiles() {
        return runnableFiles;
    }

    public void setRunnableFiles(int runnableFiles) {
        this.runnableFiles = runnableFiles;
    }

    public int getCompletedFiles() {
        return completedFiles;
    }

    public void setCompletedFiles(int completedFiles) {
        this.completedFiles = completedFiles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ImportRunEntity> getRuns() {
        return runs;
    }
}
