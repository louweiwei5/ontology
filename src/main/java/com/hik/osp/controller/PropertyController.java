package com.hik.osp.controller;

import com.hik.osp.dto.request.PropertyCreateRequest;
import com.hik.osp.dto.request.PropertyUpdateRequest;
import com.hik.osp.dto.response.PropertyResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hik.osp.entity.PropertyEntity;
import com.hik.osp.service.PropertyService;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ontologies/{ontologyId}/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<PropertyResponse> create(@PathVariable String ontologyId,
                                                    @Valid @RequestBody PropertyCreateRequest req) {
        PropertyEntity entity = propertyService.create(ontologyId, req.getName(),
                req.getPropertyType(), req.getDataType(), req.getDomainClassName(),
                req.getRange(), req.getDescription(), req.getMappingRules(),
                req.getRelationType(), req.getJunctionTableId(),
                req.getJunctionTableName(), req.getJunctionDomainColumn(),
                req.getJunctionRangeColumn());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    @GetMapping
    public ResponseEntity<List<PropertyResponse>> listAll(@PathVariable String ontologyId) {
        List<PropertyResponse> items = propertyService.listAll(ontologyId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{propId}")
    public ResponseEntity<PropertyResponse> getById(@PathVariable String ontologyId,
                                                     @PathVariable String propId) {
        return ResponseEntity.ok(toResponse(propertyService.getById(ontologyId, propId)));
    }

    @PutMapping("/{propId}")
    public ResponseEntity<PropertyResponse> update(@PathVariable String ontologyId,
                                                    @PathVariable String propId,
                                                    @RequestBody PropertyUpdateRequest req) {
        PropertyEntity entity = propertyService.update(ontologyId, propId, req.getName(),
                req.getPropertyType(), req.getDataType(), req.getDomainClassName(),
                req.getRange(), req.getDescription(), req.getMappingRules(),
                req.getRelationType(), req.getJunctionTableId(),
                req.getJunctionTableName(), req.getJunctionDomainColumn(),
                req.getJunctionRangeColumn());
        return ResponseEntity.ok(toResponse(entity));
    }

    @DeleteMapping("/{propId}")
    public ResponseEntity<Void> delete(@PathVariable String ontologyId,
                                       @PathVariable String propId) {
        propertyService.delete(ontologyId, propId);
        return ResponseEntity.noContent().build();
    }

    private PropertyResponse toResponse(PropertyEntity entity) {
        List<Map<String, String>> mappingRules = Collections.emptyList();
        if (entity.getMappingRules() != null && !entity.getMappingRules().isBlank()) {
            try {
                mappingRules = objectMapper.readValue(entity.getMappingRules(),
                        new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                mappingRules = Collections.emptyList();
            }
        }
        return PropertyResponse.builder()
                .id(entity.getId())
                .ontologyId(entity.getOntologyId())
                .name(entity.getName())
                .fullIri(entity.getFullIri())
                .propertyType(entity.getPropertyType())
                .relationType(entity.getRelationType())
                .dataType(entity.getDataType())
                .domainClassId(entity.getDomainClassId())
                .range(entity.getRange())
                .description(entity.getDescription())
                .junctionTableId(entity.getJunctionTableId())
                .junctionTableName(entity.getJunctionTableName())
                .junctionDomainColumn(entity.getJunctionDomainColumn())
                .junctionRangeColumn(entity.getJunctionRangeColumn())
                .mappingRules(mappingRules)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
