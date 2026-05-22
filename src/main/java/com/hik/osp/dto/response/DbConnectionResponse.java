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
public class DbConnectionResponse {
    private String id;
    private String name;
    private String description;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
