package com.hik.osp.controller;

import com.hik.osp.dto.request.AgentCreateRequest;
import com.hik.osp.dto.request.AgentUpdateRequest;
import com.hik.osp.dto.response.AgentDetailResponse;
import com.hik.osp.dto.response.AgentListItem;
import com.hik.osp.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public ResponseEntity<List<AgentListItem>> listAll() {
        return ResponseEntity.ok(agentService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentDetailResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(agentService.getById(id));
    }

    @PostMapping
    public ResponseEntity<AgentDetailResponse> create(@Valid @RequestBody AgentCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgentDetailResponse> update(@PathVariable String id,
                                                       @RequestBody AgentUpdateRequest req) {
        return ResponseEntity.ok(agentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        agentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
