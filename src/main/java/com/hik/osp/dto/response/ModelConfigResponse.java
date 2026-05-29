package com.hik.osp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelConfigResponse {
    private String id;
    private String name;
    private String provider;
    private String baseUrl;
    private String apiKeyMasked;
    private String modelName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
