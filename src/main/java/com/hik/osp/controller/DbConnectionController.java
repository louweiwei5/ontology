package com.hik.osp.controller;

import com.hik.osp.dto.MappingConfig;
import com.hik.osp.dto.request.DbConnectionCreateRequest;
import com.hik.osp.dto.request.DbConnectionUpdateRequest;
import com.hik.osp.dto.request.TableImportCreateRequest;
import com.hik.osp.dto.response.DbConnectionResponse;
import com.hik.osp.dto.response.TableDetail;
import com.hik.osp.dto.response.TableImportResponse;
import com.hik.osp.dto.response.TableInfo;
import com.hik.osp.entity.DbConnectionEntity;
import com.hik.osp.entity.TableImportEntity;
import com.hik.osp.service.DbConnectionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/db-connections")
@RequiredArgsConstructor
public class DbConnectionController {

    private final DbConnectionService dbConnectionService;

    @PostMapping
    public ResponseEntity<DbConnectionResponse> create(@Valid @RequestBody DbConnectionCreateRequest req) {
        DbConnectionEntity entity = dbConnectionService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    @GetMapping
    public ResponseEntity<List<DbConnectionResponse>> listAll() {
        List<DbConnectionResponse> items = dbConnectionService.listAll().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DbConnectionResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(toResponse(dbConnectionService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DbConnectionResponse> update(@PathVariable String id,
                                                        @RequestBody DbConnectionUpdateRequest req) {
        return ResponseEntity.ok(toResponse(dbConnectionService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        dbConnectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Connection test ──

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable String id) {
        return ResponseEntity.ok(dbConnectionService.testConnection(id));
    }

    // ── Table introspection ──

    @GetMapping("/{id}/tables")
    public ResponseEntity<List<TableInfo>> listTables(@PathVariable String id) {
        return ResponseEntity.ok(dbConnectionService.listTables(id));
    }

    @GetMapping("/{id}/tables/{tableName}")
    public ResponseEntity<TableDetail> getTableDetail(@PathVariable String id,
                                                       @PathVariable String tableName) {
        return ResponseEntity.ok(dbConnectionService.getTableDetail(id, tableName));
    }

    // ── Table import ──

    @PostMapping("/{id}/imports")
    public ResponseEntity<TableImportResponse> createImport(@PathVariable String id,
                                                             @Valid @RequestBody TableImportCreateRequest req) {
        TableImportEntity entity = dbConnectionService.createImport(id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toImportResponse(entity));
    }

    @PostMapping("/{id}/import")
    public ResponseEntity<TableImportResponse> createImportAlt(@PathVariable String id,
                                                                @Valid @RequestBody TableImportCreateRequest req) {
        return createImport(id, req);
    }

    @GetMapping("/imports")
    public ResponseEntity<List<Map<String, Object>>> listAllImports() {
        return ResponseEntity.ok(dbConnectionService.listImports());
    }

    @GetMapping("/imports/{importId}")
    public ResponseEntity<TableImportResponse> getImport(@PathVariable String importId) {
        return ResponseEntity.ok(toImportResponse(dbConnectionService.getImport(importId)));
    }

    @PutMapping("/imports/{importId}/mapping")
    public ResponseEntity<TableImportResponse> updateMapping(@PathVariable String importId,
                                                              @RequestBody MappingConfig config) {
        return ResponseEntity.ok(toImportResponse(dbConnectionService.updateMapping(importId, config)));
    }

    @PostMapping("/imports/{importId}/apply")
    public ResponseEntity<TableImportResponse> applyMapping(@PathVariable String importId) {
        return ResponseEntity.ok(toImportResponse(dbConnectionService.applyMapping(importId)));
    }

    // ── internal helpers ──

    private DbConnectionResponse toResponse(DbConnectionEntity entity) {
        return DbConnectionResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .dbType(entity.getDbType())
                .host(entity.getHost())
                .port(entity.getPort())
                .databaseName(entity.getDatabaseName())
                .username(entity.getUsername())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TableImportResponse toImportResponse(TableImportEntity entity) {
        return TableImportResponse.builder()
                .id(entity.getId())
                .dbConnectionId(entity.getDbConnectionId())
                .ontologyId(entity.getOntologyId())
                .status(entity.getStatus())
                .mappingJson(entity.getMappingJson())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
