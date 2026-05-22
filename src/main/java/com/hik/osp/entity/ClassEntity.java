package com.hik.osp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "classes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_class_name_per_ontology", columnNames = {"ontology_id", "name"})
})
public class ClassEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "ontology_id", length = 36, nullable = false)
    private String ontologyId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "full_iri", length = 512, nullable = false)
    private String fullIri;

    @Column(name = "parent_class_id", length = 36)
    private String parentClassId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ontology_id", insertable = false, updatable = false)
    private OntologyEntity ontology;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_class_id", insertable = false, updatable = false)
    private ClassEntity parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClassEntity> children = new ArrayList<>();

    @OneToMany(mappedBy = "domainClass")
    private List<PropertyEntity> properties = new ArrayList<>();

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
