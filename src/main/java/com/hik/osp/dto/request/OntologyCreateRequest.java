package com.hik.osp.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyCreateRequest {
    @NotBlank(message = "Ontology name is required")
    @jakarta.validation.constraints.Size(max = 255)
    private String name;

    @jakarta.validation.constraints.Size(max = 512)
    private String namespace;

    private String description;

    @jakarta.validation.constraints.Size(max = 50)
    private String version;
}
