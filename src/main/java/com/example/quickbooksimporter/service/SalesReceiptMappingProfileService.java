package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedSalesReceiptField;
import com.example.quickbooksimporter.persistence.SalesReceiptMappingProfileEntity;
import com.example.quickbooksimporter.repository.SalesReceiptMappingProfileRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesReceiptMappingProfileService {

    private final SalesReceiptMappingProfileRepository repository;

    public SalesReceiptMappingProfileService(SalesReceiptMappingProfileRepository repository) {
        this.repository = repository;
    }

    public List<MappingProfileSummary> listProfiles() {
        return repository.findByOrderByNameAsc().stream()
                .map(profile -> new MappingProfileSummary(profile.getId(), profile.getName()))
                .toList();
    }

    public Map<NormalizedSalesReceiptField, String> defaultMapping(List<String> headers) {
        Map<NormalizedSalesReceiptField, String> result = new EnumMap<>(NormalizedSalesReceiptField.class);
        for (NormalizedSalesReceiptField field : NormalizedSalesReceiptField.values()) {
            headers.stream()
                    .filter(header -> header.equalsIgnoreCase(field.sampleHeader()))
                    .findFirst()
                    .ifPresent(header -> result.put(field, header));
        }
        return result;
    }

    public Map<NormalizedSalesReceiptField, String> loadProfile(Long id) {
        SalesReceiptMappingProfileEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sales receipt mapping profile not found"));
        Map<NormalizedSalesReceiptField, String> result = new EnumMap<>(NormalizedSalesReceiptField.class);
        entity.getMappings().forEach((key, value) -> result.put(NormalizedSalesReceiptField.valueOf(key), value));
        return result;
    }

    @Transactional
    public SalesReceiptMappingProfileEntity saveProfile(String name, Map<NormalizedSalesReceiptField, String> mappings) {
        SalesReceiptMappingProfileEntity entity = new SalesReceiptMappingProfileEntity();
        entity.setName(name);
        entity.setMappings(mappings.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }
}
