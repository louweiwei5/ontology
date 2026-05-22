package com.hik.osp.entity;

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
@Table(name = "table_imports")
public class TableImportEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "db_connection_id", length = 36, nullable = false)
    private String dbConnectionId;

    @Column(name = "ontology_id", length = 36, nullable = false)
    private String ontologyId;

    @Column(name = "status", length = 50, nullable = false)
    private String status = "draft";

    @Column(name = "mapping_json", columnDefinition = "TEXT", nullable = false)
    private String mappingJson = "{}";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "db_connection_id", insertable = false, updatable = false)
    private DbConnectionEntity connection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ontology_id", insertable = false, updatable = false)
    private OntologyEntity ontology;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = "draft";
        }
        if (mappingJson == null) {
            mappingJson = "{}";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
