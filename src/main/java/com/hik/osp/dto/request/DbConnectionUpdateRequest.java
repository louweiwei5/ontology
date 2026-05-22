package com.hik.osp.dto.request;

import lombok.Data;

@Data
public class DbConnectionUpdateRequest {
    private String name;
    private String description;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
}
