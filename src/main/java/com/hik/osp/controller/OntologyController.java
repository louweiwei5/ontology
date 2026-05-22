package com.hik.osp.controller;

import com.hik.osp.dto.import_export.OntologyExportResponse;
import com.hik.osp.dto.import_export.OntologyImportRequest;
import com.hik.osp.dto.request.OntologyCreateRequest;
import com.hik.osp.dto.request.OntologyUpdateRequest;
import com.hik.osp.dto.response.GraphData;
import com.hik.osp.dto.response.OntologyResponse;
import com.hik.osp.entity.OntologyEntity;
import com.hik.osp.service.OntologyService;
import com.hik.osp.service.OwlParser;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ontologies")
@RequiredArgsConstructor
public class OntologyController {

    private final OntologyService ontologyService;
    private final OwlParser owlParser;

    @PostMapping
    public ResponseEntity<OntologyResponse> create(@Valid @RequestBody OntologyCreateRequest req) {
        OntologyEntity entity = ontologyService.create(req.getName(), req.getNamespace(),
                req.getDescription(), req.getVersion());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        return ResponseEntity.ok(ontologyService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OntologyResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(toResponse(ontologyService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OntologyResponse> update(@PathVariable String id,
                                                    @RequestBody OntologyUpdateRequest req) {
        Map<String, Object> updates = new HashMap<>();
        if (req.getName() != null) updates.put("name", req.getName());
        if (req.getNamespace() != null) updates.put("namespace", req.getNamespace());
        if (req.getDescription() != null) updates.put("description", req.getDescription());
        if (req.getVersion() != null) updates.put("version", req.getVersion());
        return ResponseEntity.ok(toResponse(ontologyService.update(id, updates)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        ontologyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Export / Import ──

    @GetMapping("/{id}/export")
    public ResponseEntity<OntologyExportResponse> exportJson(@PathVariable String id) {
        return ResponseEntity.ok(ontologyService.exportJson(id));
    }

    @PostMapping("/import")
    public ResponseEntity<OntologyResponse> importFromJson(@Valid @RequestBody OntologyImportRequest req) {
        OntologyEntity entity = ontologyService.importFromJson(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    @PostMapping(value = "/import/owl", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OntologyResponse> importOwlFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            throw new com.hik.osp.exception.BadRequestException("Empty file");
        }
        try {
            byte[] content = file.getBytes();
            OntologyImportRequest req = owlParser.parse(content);
            OntologyEntity entity = ontologyService.importFromJson(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
        } catch (java.io.IOException e) {
            throw new com.hik.osp.exception.BadRequestException("Failed to read uploaded file: " + e.getMessage());
        }
    }

    // ── Graph ──

    @GetMapping("/{id}/graph")
    public ResponseEntity<GraphData> getGraph(@PathVariable String id) {
        return ResponseEntity.ok(ontologyService.getGraph(id));
    }

    // ── TBox ──

    @GetMapping(value = "/{id}/tbox", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getTbox(@PathVariable String id) {
        return ResponseEntity.ok(ontologyService.getTbox(id));
    }

    @GetMapping(value = "/{id}/tbox/manchester", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getTboxManchester(@PathVariable String id) {
        return ResponseEntity.ok(ontologyService.getTboxManchester(id));
    }

    @GetMapping("/{id}/tbox/json")
    public ResponseEntity<Map<String, Object>> getTboxJson(@PathVariable String id) {
        return ResponseEntity.ok(ontologyService.getTboxJson(id));
    }

    // Frontend calls /tbox?format=json
    @GetMapping(value = "/{id}/tbox", params = "format=json")
    public ResponseEntity<Map<String, Object>> getTboxJsonByParam(@PathVariable String id) {
        return ResponseEntity.ok(ontologyService.getTboxJson(id));
    }

    // ── OWL Export ──

    @GetMapping(value = "/{id}/export-owl", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> exportOwl(@PathVariable String id,
                                             @RequestParam(defaultValue = "turtle") String format) {
        return ResponseEntity.ok(ontologyService.exportOwl(id, format));
    }

    // Frontend calls /export/{id}/owl (different path pattern)
    @GetMapping(value = "/{id}/export/owl", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> exportOwlAlt(@PathVariable String id,
                                                @RequestParam(defaultValue = "rdf-xml") String format) {
        return ResponseEntity.ok(ontologyService.exportOwl(id, format));
    }

    // ── internal helpers ──

    private OntologyResponse toResponse(OntologyEntity entity) {
        return OntologyResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .namespace(entity.getNamespace())
                .description(entity.getDescription())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
