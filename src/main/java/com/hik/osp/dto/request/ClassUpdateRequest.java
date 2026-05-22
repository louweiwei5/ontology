package com.hik.osp.dto.request;

import lombok.Data;

@Data
public class ClassUpdateRequest {
    private String name;
    private String description;
    private String parentClassName;
}
