package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.CsvCompareMappingPair;
import com.example.quickbooksimporter.persistence.CsvCompareMappingProfileEntity;
import com.example.quickbooksimporter.repository.CsvCompareMappingProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CsvCompareMappingProfileServiceTest {

    @Mock
    private CsvCompareMappingProfileRepository repository;

    @InjectMocks
    private CsvCompareMappingProfileService service;

    @Captor
    private ArgumentCaptor<CsvCompareMappingProfileEntity> entityCaptor;

    @Test
    void savesDynamicPairMappings() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveProfile("compare-1", List.of(
                new CsvCompareMappingPair(1, "Invoice #", "InvoiceNo"),
                new CsvCompareMappingPair(2, "Client", "Customer")));

        org.mockito.Mockito.verify(repository).save(entityCaptor.capture());
        CsvCompareMappingProfileEntity saved = entityCaptor.getValue();
        assertThat(saved.getMappings()).containsEntry("pair.count", "2");
        assertThat(saved.getMappings()).containsEntry("pair.1.file1", "Invoice #");
        assertThat(saved.getMappings()).containsEntry("pair.1.file2", "InvoiceNo");
        assertThat(saved.getMappings()).containsEntry("pair.2.file1", "Client");
        assertThat(saved.getMappings()).containsEntry("pair.2.file2", "Customer");
    }

    @Test
    void loadsMappingsFromStoredProfile() {
        CsvCompareMappingProfileEntity entity = new CsvCompareMappingProfileEntity();
        entity.setMappings(Map.of(
                "pair.count", "3",
                "pair.1.file1", "A1",
                "pair.1.file2", "A2",
                "pair.2.file1", "B1",
                "pair.2.file2", "B2",
                "pair.3.file1", "C1",
                "pair.3.file2", "C2"));
        when(repository.findById(9L)).thenReturn(Optional.of(entity));

        List<CsvCompareMappingPair> loaded = service.loadProfile(9L);

        assertThat(loaded).hasSize(3);
        assertThat(loaded.get(0).file1Header()).isEqualTo("A1");
        assertThat(loaded.get(0).file2Header()).isEqualTo("A2");
        assertThat(loaded.get(2).file1Header()).isEqualTo("C1");
    }
}
