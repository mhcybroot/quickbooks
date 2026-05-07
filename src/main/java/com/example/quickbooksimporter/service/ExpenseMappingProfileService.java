package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedExpenseField;
import com.example.quickbooksimporter.persistence.ExpenseMappingProfileEntity;
import com.example.quickbooksimporter.repository.ExpenseMappingProfileRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseMappingProfileService {

    private final ExpenseMappingProfileRepository repository;

    public ExpenseMappingProfileService(ExpenseMappingProfileRepository repository) {
        this.repository = repository;
    }

    public List<MappingProfileSummary> listProfiles() {
        return repository.findByOrderByNameAsc().stream()
                .map(profile -> new MappingProfileSummary(profile.getId(), profile.getName()))
                .toList();
    }

    public Map<NormalizedExpenseField, String> defaultExpenseMapping(List<String> headers) {
        Map<NormalizedExpenseField, String> result = new EnumMap<>(NormalizedExpenseField.class);
        for (NormalizedExpenseField field : NormalizedExpenseField.values()) {
            headers.stream()
                    .filter(header -> header.equalsIgnoreCase(field.sampleHeader()))
                    .findFirst()
                    .ifPresent(header -> result.put(field, header));
        }
        return result;
    }

    public Map<NormalizedExpenseField, String> loadProfile(Long id) {
        ExpenseMappingProfileEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense mapping profile not found"));
        Map<NormalizedExpenseField, String> result = new EnumMap<>(NormalizedExpenseField.class);
        entity.getMappings().forEach((key, value) -> result.put(NormalizedExpenseField.valueOf(key), value));
        return result;
    }

    @Transactional
    public ExpenseMappingProfileEntity saveProfile(String name, Map<NormalizedExpenseField, String> mappings) {
        ExpenseMappingProfileEntity entity = new ExpenseMappingProfileEntity();
        entity.setName(name);
        entity.setMappings(mappings.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }
}
