package com.hik.osp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableImportCreateRequest {
    @NotBlank(message = "Ontology ID is required")
    private String ontologyId;

    @NotEmpty(message = "At least one table name is required")
    private List<String> tables;
}
