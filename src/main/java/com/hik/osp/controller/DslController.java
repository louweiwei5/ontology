package com.hik.osp.controller;

import com.hik.osp.dto.request.DslQueryRequest;
import com.hik.osp.dto.response.DslQueryResponse;
import com.hik.osp.service.DslQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dsl")
@RequiredArgsConstructor
public class DslController {

    private final DslQueryService dslQueryService;

    @PostMapping("/query")
    public ResponseEntity<DslQueryResponse> query(@RequestBody DslQueryRequest request) {
        DslQueryResponse response = dslQueryService.executeQuery(request);
        return ResponseEntity.ok(response);
    }
}
