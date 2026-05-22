package com.hik.osp.entity;

import com.hik.osp.enums.DataType;
import com.hik.osp.enums.PropertyType;
import com.hik.osp.enums.RelationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "properties", uniqueConstraints = {
        @UniqueConstraint(name = "uq_property_name_per_ontology", columnNames = {"ontology_id", "name", "domain_class_id"})
})
public class PropertyEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "ontology_id", length = 36, nullable = false)
    private String ontologyId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "full_iri", length = 512, nullable = false)
    private String fullIri;

    @Column(name = "property_type", length = 50, nullable = false)
    private PropertyType propertyType;

    @Column(name = "relation_type", length = 20)
    private RelationType relationType;

    @Column(name = "junction_table_id", length = 36)
    private String junctionTableId;

    @Column(name = "junction_table_name", length = 255)
    private String junctionTableName;

    @Column(name = "junction_domain_column", length = 255)
    private String junctionDomainColumn;

    @Column(name = "junction_range_column", length = 255)
    private String junctionRangeColumn;

    @Column(name = "data_type", length = 50)
    private DataType dataType;

    @Column(name = "domain_class_id", length = 36)
    private String domainClassId;

    @Column(name = "`range`", length = 512)
    private String range;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "mapping_rules", columnDefinition = "TEXT")
    private String mappingRules;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ontology_id", insertable = false, updatable = false)
    private OntologyEntity ontology;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_class_id", insertable = false, updatable = false)
    private ClassEntity domainClass;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
