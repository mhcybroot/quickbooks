package com.example.quickbooksimporter.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "reconciliation_session")
public class ReconciliationSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @Column(nullable = false)
    private String sourceFileName;

    @Column(nullable = false)
    private boolean dryRun;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int autoMatchedCount;

    @Column(nullable = false)
    private int needsReviewCount;

    @Column(nullable = false)
    private int bankOnlyCount;

    @Column(nullable = false)
    private int qboOnlyCount;

    @Column(nullable = false)
    private int appliedCount;

    @Column(nullable = false)
    private int failedCount;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ReconciliationSessionRowEntity> rows = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAutoMatchedCount() {
        return autoMatchedCount;
    }

    public void setAutoMatchedCount(int autoMatchedCount) {
        this.autoMatchedCount = autoMatchedCount;
    }

    public int getNeedsReviewCount() {
        return needsReviewCount;
    }

    public void setNeedsReviewCount(int needsReviewCount) {
        this.needsReviewCount = needsReviewCount;
    }

    public int getBankOnlyCount() {
        return bankOnlyCount;
    }

    public void setBankOnlyCount(int bankOnlyCount) {
        this.bankOnlyCount = bankOnlyCount;
    }

    public int getQboOnlyCount() {
        return qboOnlyCount;
    }

    public void setQboOnlyCount(int qboOnlyCount) {
        this.qboOnlyCount = qboOnlyCount;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public void setAppliedCount(int appliedCount) {
        this.appliedCount = appliedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
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

    public List<ReconciliationSessionRowEntity> getRows() {
        return rows;
    }
}
