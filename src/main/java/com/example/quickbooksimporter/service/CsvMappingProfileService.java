package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.persistence.CsvMappingProfileEntity;
import com.example.quickbooksimporter.repository.CsvMappingProfileRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvMappingProfileService {

    private final CsvMappingProfileRepository repository;

    public CsvMappingProfileService(CsvMappingProfileRepository repository) {
        this.repository = repository;
    }

    public List<MappingProfileSummary> listProfiles() {
        return repository.findByEntityTypeOrderByNameAsc(EntityType.INVOICE).stream()
                .map(profile -> new MappingProfileSummary(profile.getId(), profile.getName()))
                .toList();
    }

    public Map<NormalizedInvoiceField, String> defaultInvoiceMapping(List<String> headers) {
        Map<NormalizedInvoiceField, String> result = new EnumMap<>(NormalizedInvoiceField.class);
        for (NormalizedInvoiceField field : NormalizedInvoiceField.values()) {
            headers.stream()
                    .filter(header -> header.equalsIgnoreCase(field.sampleHeader()))
                    .findFirst()
                    .ifPresent(header -> result.put(field, header));
        }
        return result;
    }

    public Map<NormalizedInvoiceField, String> loadProfile(Long id) {
        return new EnumMap<>(repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mapping profile not found"))
                .getMappings());
    }

    @Transactional
    public CsvMappingProfileEntity saveProfile(String name, Map<NormalizedInvoiceField, String> mappings) {
        CsvMappingProfileEntity entity = new CsvMappingProfileEntity();
        entity.setName(name);
        entity.setEntityType(EntityType.INVOICE);
        entity.setMappings(new EnumMap<>(mappings));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }
}
