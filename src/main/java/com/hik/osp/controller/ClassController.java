package com.hik.osp.controller;

import com.hik.osp.dto.request.ClassCreateRequest;
import com.hik.osp.dto.request.ClassUpdateRequest;
import com.hik.osp.dto.response.ClassResponse;
import com.hik.osp.entity.ClassEntity;
import com.hik.osp.service.ClassService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ontologies/{ontologyId}/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    @PostMapping
    public ResponseEntity<ClassResponse> create(@PathVariable String ontologyId,
                                                 @Valid @RequestBody ClassCreateRequest req) {
        ClassEntity entity = classService.create(ontologyId, req.getName(),
                req.getDescription(), req.getParentClassName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    @GetMapping
    public ResponseEntity<List<ClassResponse>> listAll(@PathVariable String ontologyId) {
        List<ClassResponse> items = classService.listAll(ontologyId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{classId}")
    public ResponseEntity<ClassResponse> getById(@PathVariable String ontologyId,
                                                  @PathVariable String classId) {
        return ResponseEntity.ok(toResponse(classService.getById(ontologyId, classId)));
    }

    @PutMapping("/{classId}")
    public ResponseEntity<ClassResponse> update(@PathVariable String ontologyId,
                                                 @PathVariable String classId,
                                                 @RequestBody ClassUpdateRequest req) {
        ClassEntity entity = classService.update(ontologyId, classId, req.getName(),
                req.getDescription(), req.getParentClassName());
        return ResponseEntity.ok(toResponse(entity));
    }

    @DeleteMapping("/{classId}")
    public ResponseEntity<Void> delete(@PathVariable String ontologyId,
                                        @PathVariable String classId) {
        classService.delete(ontologyId, classId);
        return ResponseEntity.noContent().build();
    }

    private ClassResponse toResponse(ClassEntity entity) {
        return ClassResponse.builder()
                .id(entity.getId())
                .ontologyId(entity.getOntologyId())
                .name(entity.getName())
                .fullIri(entity.getFullIri())
                .parentClassId(entity.getParentClassId())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
