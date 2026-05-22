package com.hik.osp.dto.import_export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyExportResponse {
    private String name;
    private String namespace;
    private String description;
    private String version;
    private List<ClassExportItem> classes;
    private List<PropertyExportItem> properties;
}
