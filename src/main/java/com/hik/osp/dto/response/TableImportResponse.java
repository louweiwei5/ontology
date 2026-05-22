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
public class TableImportResponse {
    private String id;
    private String dbConnectionId;
    private String ontologyId;
    private String status;
    private String mappingJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
