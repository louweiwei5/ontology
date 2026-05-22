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
public class OntologyListItem {
    private String id;
    private String name;
    private String namespace;
    private String description;
    private String version;
    private long classCount;
    private long propertyCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
