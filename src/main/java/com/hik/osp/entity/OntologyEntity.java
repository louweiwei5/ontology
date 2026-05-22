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
@Table(name = "ontologies")
public class OntologyEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", length = 255, nullable = false, unique = true)
    private String name;

    @Column(name = "namespace", length = 512, nullable = false)
    private String namespace = "";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "version", length = 50, nullable = false)
    private String version = "1.0.0";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "ontology", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClassEntity> classes = new ArrayList<>();

    @OneToMany(mappedBy = "ontology", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PropertyEntity> properties = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (namespace == null) {
            namespace = "";
        }
        if (version == null) {
            version = "1.0.0";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
