package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.AppJobStatus;
import com.example.quickbooksimporter.domain.AppJobType;
import com.example.quickbooksimporter.persistence.AppJobEntity;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.repository.AppJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppJobService {

    private final AppJobRepository appJobRepository;
    private final CurrentCompanyService currentCompanyService;
    private final ObjectMapper objectMapper;

    public AppJobService(AppJobRepository appJobRepository,
                         CurrentCompanyService currentCompanyService,
                         ObjectMapper objectMapper) {
        this.appJobRepository = appJobRepository;
        this.currentCompanyService = currentCompanyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AppJobEntity createForCurrentCompany(AppJobType type, String description, int totalUnits) {
        return create(currentCompanyService.requireCurrentCompany(), type, description, totalUnits);
    }

    @Transactional
    public AppJobEntity create(CompanyEntity company, AppJobType type, String description, int totalUnits) {
        AppJobEntity job = new AppJobEntity();
        job.setCompany(company);
        job.setType(type);
        job.setStatus(AppJobStatus.QUEUED);
        job.setDescription(description);
        job.setTotalUnits(Math.max(0, totalUnits));
        job.setCompletedUnits(0);
        job.setSummaryMessage("Queued");
        job.setCreatedAt(Instant.now());
        return appJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<AppJobSnapshot> findSnapshot(Long jobId) {
        Long companyId = currentCompanyService.requireCurrentCompanyId();
        if (companyId == null) {
            return Optional.empty();
        }
        return appJobRepository.findByIdAndCompanyId(jobId, companyId).map(this::toSnapshot);
    }

    @Transactional(readOnly = true)
    public AppJobEntity requireJob(Long jobId, Long companyId) {
        return appJobRepository.findByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    @Transactional
    public void markRunning(Long jobId, Long companyId, String summaryMessage) {
        AppJobEntity job = requireJob(jobId, companyId);
        job.setStatus(AppJobStatus.RUNNING);
        if (job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        }
        job.setCompletedAt(null);
        job.setSummaryMessage(summaryMessage);
        appJobRepository.save(job);
    }

    @Transactional
    public void updateProgress(Long jobId, Long companyId, int completedUnits, int totalUnits, String summaryMessage) {
        AppJobEntity job = requireJob(jobId, companyId);
        if (job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        }
        job.setStatus(AppJobStatus.RUNNING);
        job.setCompletedUnits(Math.max(0, completedUnits));
        job.setTotalUnits(Math.max(0, totalUnits));
        job.setSummaryMessage(summaryMessage);
        appJobRepository.save(job);
    }

    @Transactional
    public void completeSuccess(Long jobId, Long companyId, String summaryMessage, Object resultPayload) {
        AppJobEntity job = requireJob(jobId, companyId);
        job.setStatus(AppJobStatus.SUCCEEDED);
        job.setCompletedUnits(job.getTotalUnits());
        job.setSummaryMessage(summaryMessage);
        job.setCompletedAt(Instant.now());
        job.setResultPayload(writeJson(resultPayload));
        appJobRepository.save(job);
    }

    @Transactional
    public void completeFailure(Long jobId, Long companyId, String summaryMessage) {
        AppJobEntity job = requireJob(jobId, companyId);
        job.setStatus(AppJobStatus.FAILED);
        job.setSummaryMessage(summaryMessage);
        job.setCompletedAt(Instant.now());
        appJobRepository.save(job);
    }

    public <T> T readResult(String resultPayload, Class<T> resultType) {
        if (resultPayload == null || resultPayload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(resultPayload, resultType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize job payload", exception);
        }
    }

    public String writeJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize job payload", exception);
        }
    }

    private AppJobSnapshot toSnapshot(AppJobEntity job) {
        double progressValue = job.getTotalUnits() <= 0
                ? (job.getStatus() == AppJobStatus.SUCCEEDED ? 1d : 0d)
                : Math.min((double) job.getCompletedUnits() / job.getTotalUnits(), 1d);
        return new AppJobSnapshot(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getDescription(),
                job.getTotalUnits(),
                job.getCompletedUnits(),
                progressValue,
                formatPercentLabel(job, progressValue),
                job.getSummaryMessage(),
                job.getResultPayload(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt());
    }

    private String formatPercentLabel(AppJobEntity job, double progressValue) {
        if (job.getStatus() == AppJobStatus.SUCCEEDED) {
            return "100%";
        }
        if (job.getTotalUnits() > 0 && job.getCompletedUnits() > 0 && progressValue < 0.01d) {
            return "<1%";
        }
        return String.format("%.0f%%", progressValue * 100d);
    }
}
