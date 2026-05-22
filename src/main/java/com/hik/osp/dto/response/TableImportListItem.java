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
public class TableImportListItem {
    private String id;
    private String dbConnectionId;
    private String ontologyId;
    private String status;
    private String connectionName;
    private String ontologyName;
    private LocalDateTime createdAt;
}
