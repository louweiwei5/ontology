package com.hik.osp.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "HIK_OSP - Ontology Semantic Platform");
        result.put("status", "running");
        result.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(result);
    }
}
