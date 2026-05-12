package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.CsvCompareMappingPair;
import com.example.quickbooksimporter.persistence.CsvCompareMappingProfileEntity;
import com.example.quickbooksimporter.repository.CsvCompareMappingProfileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvCompareMappingProfileService {

    private final CsvCompareMappingProfileRepository repository;

    public CsvCompareMappingProfileService(CsvCompareMappingProfileRepository repository) {
        this.repository = repository;
    }

    public List<MappingProfileSummary> listProfiles() {
        return repository.findByOrderByNameAsc().stream()
                .map(profile -> new MappingProfileSummary(profile.getId(), profile.getName()))
                .toList();
    }

    public List<CsvCompareMappingPair> defaultMapping(List<String> file1Headers, List<String> file2Headers, int pairCount) {
        Map<String, String> file2ByLower = new HashMap<>();
        file2Headers.forEach(header -> file2ByLower.putIfAbsent(header.toLowerCase(), header));

        List<CsvCompareMappingPair> pairs = new ArrayList<>();
        for (int i = 1; i <= pairCount; i++) {
            String file1Header = null;
            String file2Header = null;
            if (i <= file1Headers.size()) {
                String candidate = file1Headers.get(i - 1);
                file1Header = candidate;
                file2Header = file2ByLower.get(candidate.toLowerCase());
            }
            pairs.add(new CsvCompareMappingPair(i, file1Header, file2Header));
        }
        return pairs;
    }

    public List<CsvCompareMappingPair> loadProfile(Long id) {
        CsvCompareMappingProfileEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CSV compare mapping profile not found"));
        int count = parseCount(entity.getMappings().get("pair.count"));
        return IntStream.rangeClosed(1, Math.max(1, count))
                .mapToObj(index -> new CsvCompareMappingPair(
                        index,
                        entity.getMappings().get("pair." + index + ".file1"),
                        entity.getMappings().get("pair." + index + ".file2")))
                .toList();
    }

    @Transactional
    public CsvCompareMappingProfileEntity saveProfile(String name, List<CsvCompareMappingPair> pairs) {
        CsvCompareMappingProfileEntity entity = new CsvCompareMappingProfileEntity();
        entity.setName(name);
        Map<String, String> mappings = new HashMap<>();
        mappings.put("pair.count", String.valueOf(pairs.size()));
        for (CsvCompareMappingPair pair : pairs) {
            if (StringUtils.isNotBlank(pair.file1Header())) {
                mappings.put("pair." + pair.index() + ".file1", pair.file1Header());
            }
            if (StringUtils.isNotBlank(pair.file2Header())) {
                mappings.put("pair." + pair.index() + ".file2", pair.file2Header());
            }
        }
        entity.setMappings(mappings);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }

    private int parseCount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return 1;
        }
    }
}
