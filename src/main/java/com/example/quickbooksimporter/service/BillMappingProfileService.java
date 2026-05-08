package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedBillField;
import com.example.quickbooksimporter.persistence.BillMappingProfileEntity;
import com.example.quickbooksimporter.repository.BillMappingProfileRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillMappingProfileService {
    private final BillMappingProfileRepository repository;

    public BillMappingProfileService(BillMappingProfileRepository repository) {
        this.repository = repository;
    }

    public List<MappingProfileSummary> listProfiles() {
        return repository.findByOrderByNameAsc().stream()
                .map(p -> new MappingProfileSummary(p.getId(), p.getName()))
                .toList();
    }

    public Map<NormalizedBillField, String> defaultMapping(List<String> headers) {
        Map<NormalizedBillField, String> result = new EnumMap<>(NormalizedBillField.class);
        for (NormalizedBillField field : NormalizedBillField.values()) {
            headers.stream().filter(h -> h.equalsIgnoreCase(field.sampleHeader())).findFirst().ifPresent(h -> result.put(field, h));
        }
        return result;
    }

    public Map<NormalizedBillField, String> loadProfile(Long id) {
        BillMappingProfileEntity entity = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Bill mapping profile not found"));
        Map<NormalizedBillField, String> result = new EnumMap<>(NormalizedBillField.class);
        entity.getMappings().forEach((k, v) -> result.put(NormalizedBillField.valueOf(k), v));
        return result;
    }

    @Transactional
    public BillMappingProfileEntity saveProfile(String name, Map<NormalizedBillField, String> mappings) {
        BillMappingProfileEntity entity = new BillMappingProfileEntity();
        entity.setName(name);
        entity.setMappings(mappings.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getValue()))
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }
}
