package com.example.quickbooksimporter.persistence;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Table(name = "csv_mapping_profile")
public class CsvMappingProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntityType entityType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "csv_mapping_profile_entries", joinColumns = @JoinColumn(name = "mapping_profile_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "target_field")
    @Column(name = "source_header", nullable = false)
    private Map<NormalizedInvoiceField, String> mappings = new EnumMap<>(NormalizedInvoiceField.class);

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public Map<NormalizedInvoiceField, String> getMappings() {
        return mappings;
    }

    public void setMappings(Map<NormalizedInvoiceField, String> mappings) {
        this.mappings = mappings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
