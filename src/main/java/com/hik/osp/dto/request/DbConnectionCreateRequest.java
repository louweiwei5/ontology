package com.hik.osp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbConnectionCreateRequest {
    @NotBlank(message = "Connection name is required")
    @Size(max = 255)
    private String name;

    private String description;

    @Builder.Default
    private String dbType = "mysql";

    @NotBlank(message = "Host is required")
    @Size(max = 255)
    private String host;

    @Builder.Default
    private Integer port = 3306;

    @NotBlank(message = "Database name is required")
    @Size(max = 255)
    private String databaseName;

    @NotBlank(message = "Username is required")
    @Size(max = 255)
    private String username;

    @Size(max = 255)
    private String password;
}
