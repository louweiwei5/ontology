package com.hik.osp.dto.request;

import lombok.Data;

@Data
public class ModelConfigUpdateRequest {
    private String name;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelName;
}
