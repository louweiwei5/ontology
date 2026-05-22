package com.hik.osp.dto.request;

import lombok.Data;

@Data
public class OntologyUpdateRequest {
    private String name;
    private String namespace;
    private String description;
    private String version;
}
