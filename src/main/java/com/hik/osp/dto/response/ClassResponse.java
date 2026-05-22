package com.hik.osp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassResponse {
    private String id;
    private String ontologyId;
    private String name;
    private String fullIri;
    private String parentClassId;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
