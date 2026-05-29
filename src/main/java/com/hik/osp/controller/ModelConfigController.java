package com.hik.osp.controller;

import com.hik.osp.dto.request.ModelConfigCreateRequest;
import com.hik.osp.dto.request.ModelConfigUpdateRequest;
import com.hik.osp.dto.response.ModelConfigResponse;
import com.hik.osp.service.ModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping
    public ResponseEntity<List<ModelConfigResponse>> listAll() {
        return ResponseEntity.ok(modelConfigService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModelConfigResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(modelConfigService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ModelConfigResponse> create(@Valid @RequestBody ModelConfigCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(modelConfigService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelConfigResponse> update(@PathVariable String id,
                                                       @RequestBody ModelConfigUpdateRequest req) {
        return ResponseEntity.ok(modelConfigService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        modelConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
