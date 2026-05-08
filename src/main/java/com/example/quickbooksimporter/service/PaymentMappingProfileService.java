package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedPaymentField;
import com.example.quickbooksimporter.persistence.PaymentMappingProfileEntity;
import com.example.quickbooksimporter.repository.PaymentMappingProfileRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMappingProfileService {
    public static final String DATE_FORMAT_PAYMENT_DATE_KEY = "__DATE_FORMAT_PAYMENT_DATE";

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
        entity.getMappings().forEach((key, value) -> {
            try {
                result.put(NormalizedPaymentField.valueOf(key), value);
            } catch (IllegalArgumentException ignored) {
            }
        });
        return result;
    }

    public DateFormatOption loadPaymentDateFormat(Long id) {
        PaymentMappingProfileEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment mapping profile not found"));
        return DateFormatOption.fromStored(entity.getMappings().get(DATE_FORMAT_PAYMENT_DATE_KEY));
    }

    @Transactional
    public PaymentMappingProfileEntity saveProfile(String name, Map<NormalizedPaymentField, String> mappings) {
        return saveProfile(name, mappings, DateFormatOption.AUTO);
    }

    @Transactional
    public PaymentMappingProfileEntity saveProfile(String name,
                                                   Map<NormalizedPaymentField, String> mappings,
                                                   DateFormatOption paymentDateFormat) {
        PaymentMappingProfileEntity entity = new PaymentMappingProfileEntity();
        entity.setName(name);
        Map<String, String> entries = mappings.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getValue()))
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        entries.put(DATE_FORMAT_PAYMENT_DATE_KEY, (paymentDateFormat == null ? DateFormatOption.AUTO : paymentDateFormat).name());
        entity.setMappings(entries);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }
}
