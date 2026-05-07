package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedPaymentField;
import com.example.quickbooksimporter.persistence.PaymentMappingProfileEntity;
import com.example.quickbooksimporter.repository.PaymentMappingProfileRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMappingProfileService {

    private final PaymentMappingProfileRepository repository;

    public PaymentMappingProfileService(PaymentMappingProfileRepository repository) {
        this.repository = repository;
    }

    public List<MappingProfileSummary> listProfiles() {
        return repository.findByOrderByNameAsc().stream()
                .map(profile -> new MappingProfileSummary(profile.getId(), profile.getName()))
                .toList();
    }

    public Map<NormalizedPaymentField, String> defaultPaymentMapping(List<String> headers) {
        Map<NormalizedPaymentField, String> result = new EnumMap<>(NormalizedPaymentField.class);
        for (NormalizedPaymentField field : NormalizedPaymentField.values()) {
            headers.stream()
                    .filter(header -> header.equalsIgnoreCase(field.sampleHeader()))
                    .findFirst()
                    .ifPresent(header -> result.put(field, header));
        }
        return result;
    }

    public Map<NormalizedPaymentField, String> loadProfile(Long id) {
        PaymentMappingProfileEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment mapping profile not found"));
        Map<NormalizedPaymentField, String> result = new EnumMap<>(NormalizedPaymentField.class);
        entity.getMappings().forEach((key, value) -> result.put(NormalizedPaymentField.valueOf(key), value));
        return result;
    }

    @Transactional
    public PaymentMappingProfileEntity saveProfile(String name, Map<NormalizedPaymentField, String> mappings) {
        PaymentMappingProfileEntity entity = new PaymentMappingProfileEntity();
        entity.setName(name);
        entity.setMappings(mappings.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }
}
