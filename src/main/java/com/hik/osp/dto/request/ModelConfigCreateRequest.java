package com.hik.osp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModelConfigCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String provider;
    @NotBlank
    private String baseUrl;
    @NotBlank
    private String apiKey;
    @NotBlank
    private String modelName;
}
