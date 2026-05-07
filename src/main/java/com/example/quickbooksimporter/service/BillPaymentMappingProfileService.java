package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedBillPaymentField;
import com.example.quickbooksimporter.persistence.BillPaymentMappingProfileEntity;
import com.example.quickbooksimporter.repository.BillPaymentMappingProfileRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillPaymentMappingProfileService {
    private final BillPaymentMappingProfileRepository repository;

    public BillPaymentMappingProfileService(BillPaymentMappingProfileRepository repository) {
        this.repository = repository;
    }

    public List<MappingProfileSummary> listProfiles() {
        return repository.findByOrderByNameAsc().stream()
                .map(p -> new MappingProfileSummary(p.getId(), p.getName()))
                .toList();
    }

    public Map<NormalizedBillPaymentField, String> defaultMapping(List<String> headers) {
        Map<NormalizedBillPaymentField, String> result = new EnumMap<>(NormalizedBillPaymentField.class);
        for (NormalizedBillPaymentField field : NormalizedBillPaymentField.values()) {
            headers.stream().filter(h -> h.equalsIgnoreCase(field.sampleHeader())).findFirst().ifPresent(h -> result.put(field, h));
        }
        return result;
    }

    public Map<NormalizedBillPaymentField, String> loadProfile(Long id) {
        BillPaymentMappingProfileEntity entity = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Bill payment mapping profile not found"));
        Map<NormalizedBillPaymentField, String> result = new EnumMap<>(NormalizedBillPaymentField.class);
        entity.getMappings().forEach((k, v) -> result.put(NormalizedBillPaymentField.valueOf(k), v));
        return result;
    }

    @Transactional
    public BillPaymentMappingProfileEntity saveProfile(String name, Map<NormalizedBillPaymentField, String> mappings) {
        BillPaymentMappingProfileEntity entity = new BillPaymentMappingProfileEntity();
        entity.setName(name);
        entity.setMappings(mappings.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }
}
