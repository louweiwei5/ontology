package com.hik.osp.dto.import_export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyImportRequest {
    @NotBlank(message = "Ontology name is required")
    @Size(max = 255)
    private String name;

    private String namespace;

    private String description;

    private String version;

    @Builder.Default
    private List<ClassImportItem> classes = List.of();

    @Builder.Default
    private List<PropertyImportItem> properties = List.of();
}
