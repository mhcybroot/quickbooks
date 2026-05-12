package com.example.quickbooksimporter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "reconciliation_session_row")
public class ReconciliationSessionRowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    private ReconciliationSessionEntity session;

    @Column(nullable = false)
    private int bankRowNumber;

    private LocalDate bankTxnDate;

    @Column(precision = 18, scale = 2)
    private BigDecimal bankAmount;

    private String bankReference;

    @Column(columnDefinition = "text")
    private String bankMemo;

    private String bankCounterparty;

    private String qboTxnId;

    private String qboSyncToken;

    private String qboEntityType;

    private LocalDate qboTxnDate;

    @Column(precision = 18, scale = 2)
    private BigDecimal qboAmount;

    private String qboReference;

    private String qboParty;

    @Column(columnDefinition = "text")
    private String candidateTxnIds;

    private Integer candidateCount;

    private String groupKey;

    private LocalDate groupWindowStart;

    private LocalDate groupWindowEnd;

    private String allocationMode;

    @Column(nullable = false)
    private boolean batchMatch;

    private String patternType;

    @Column(columnDefinition = "text")
    private String patternKey;

    private String woKey;

    @Column(nullable = false)
    private boolean woMatched;

    private String woSource;

    private String tier;

    private int confidence;

    private String disposition;

    @Column(columnDefinition = "text")
    private String rationale;

    @Column(nullable = false)
    private boolean applied;

    @Column(nullable = false)
    private boolean applySuccess;

    private String intuitTid;

    @Column(columnDefinition = "text")
    private String applyMessage;

    @Column(nullable = false)
    private int applySuccessCount;

    @Column(nullable = false)
    private int applyFailCount;

    public Long getId() {
        return id;
    }

    public ReconciliationSessionEntity getSession() {
        return session;
    }

    public void setSession(ReconciliationSessionEntity session) {
        this.session = session;
    }

    public int getBankRowNumber() {
        return bankRowNumber;
    }

    public void setBankRowNumber(int bankRowNumber) {
        this.bankRowNumber = bankRowNumber;
    }

    public LocalDate getBankTxnDate() {
        return bankTxnDate;
    }

    public void setBankTxnDate(LocalDate bankTxnDate) {
        this.bankTxnDate = bankTxnDate;
    }

    public BigDecimal getBankAmount() {
        return bankAmount;
    }

    public void setBankAmount(BigDecimal bankAmount) {
        this.bankAmount = bankAmount;
    }

    public String getBankReference() {
        return bankReference;
    }

    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }

    public String getBankMemo() {
        return bankMemo;
    }

    public void setBankMemo(String bankMemo) {
        this.bankMemo = bankMemo;
    }

    public String getBankCounterparty() {
        return bankCounterparty;
    }

    public void setBankCounterparty(String bankCounterparty) {
        this.bankCounterparty = bankCounterparty;
    }

    public String getQboTxnId() {
        return qboTxnId;
    }

    public void setQboTxnId(String qboTxnId) {
        this.qboTxnId = qboTxnId;
    }

    public String getQboSyncToken() {
        return qboSyncToken;
    }

    public void setQboSyncToken(String qboSyncToken) {
        this.qboSyncToken = qboSyncToken;
    }

    public String getQboEntityType() {
        return qboEntityType;
    }

    public void setQboEntityType(String qboEntityType) {
        this.qboEntityType = qboEntityType;
    }

    public LocalDate getQboTxnDate() {
        return qboTxnDate;
    }

    public void setQboTxnDate(LocalDate qboTxnDate) {
        this.qboTxnDate = qboTxnDate;
    }

    public BigDecimal getQboAmount() {
        return qboAmount;
    }

    public void setQboAmount(BigDecimal qboAmount) {
        this.qboAmount = qboAmount;
    }

    public String getQboReference() {
        return qboReference;
    }

    public void setQboReference(String qboReference) {
        this.qboReference = qboReference;
    }

    public String getQboParty() {
        return qboParty;
    }

    public void setQboParty(String qboParty) {
        this.qboParty = qboParty;
    }

    public String getCandidateTxnIds() {
        return candidateTxnIds;
    }

    public void setCandidateTxnIds(String candidateTxnIds) {
        this.candidateTxnIds = candidateTxnIds;
    }

    public Integer getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(Integer candidateCount) {
        this.candidateCount = candidateCount;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public LocalDate getGroupWindowStart() {
        return groupWindowStart;
    }

    public void setGroupWindowStart(LocalDate groupWindowStart) {
        this.groupWindowStart = groupWindowStart;
    }

    public LocalDate getGroupWindowEnd() {
        return groupWindowEnd;
    }

    public void setGroupWindowEnd(LocalDate groupWindowEnd) {
        this.groupWindowEnd = groupWindowEnd;
    }

    public String getAllocationMode() {
        return allocationMode;
    }

    public void setAllocationMode(String allocationMode) {
        this.allocationMode = allocationMode;
    }

    public boolean isBatchMatch() {
        return batchMatch;
    }

    public void setBatchMatch(boolean batchMatch) {
        this.batchMatch = batchMatch;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getPatternType() {
        return patternType;
    }

    public void setPatternType(String patternType) {
        this.patternType = patternType;
    }

    public String getPatternKey() {
        return patternKey;
    }

    public void setPatternKey(String patternKey) {
        this.patternKey = patternKey;
    }

    public String getWoKey() {
        return woKey;
    }

    public void setWoKey(String woKey) {
        this.woKey = woKey;
    }

    public boolean isWoMatched() {
        return woMatched;
    }

    public void setWoMatched(boolean woMatched) {
        this.woMatched = woMatched;
    }

    public String getWoSource() {
        return woSource;
    }

    public void setWoSource(String woSource) {
        this.woSource = woSource;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public boolean isApplySuccess() {
        return applySuccess;
    }

    public void setApplySuccess(boolean applySuccess) {
        this.applySuccess = applySuccess;
    }

    public String getIntuitTid() {
        return intuitTid;
    }

    public void setIntuitTid(String intuitTid) {
        this.intuitTid = intuitTid;
    }

    public String getApplyMessage() {
        return applyMessage;
    }

    public void setApplyMessage(String applyMessage) {
        this.applyMessage = applyMessage;
    }

    public int getApplySuccessCount() {
        return applySuccessCount;
    }

    public void setApplySuccessCount(int applySuccessCount) {
        this.applySuccessCount = applySuccessCount;
    }

    public int getApplyFailCount() {
        return applyFailCount;
    }

    public void setApplyFailCount(int applyFailCount) {
        this.applyFailCount = applyFailCount;
    }
}
