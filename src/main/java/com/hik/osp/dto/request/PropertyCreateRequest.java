package com.hik.osp.dto.request;

import com.hik.osp.enums.DataType;
import com.hik.osp.enums.PropertyType;
import com.hik.osp.enums.RelationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyCreateRequest {
    @NotBlank(message = "Property name is required")
    @Size(max = 255)
    private String name;

    @NotNull(message = "Property type is required")
    private PropertyType propertyType;

    private RelationType relationType;

    private DataType dataType;

    private String domainClassName;

    private String range;

    private String description;

    private List<Map<String, String>> mappingRules;

    private String junctionTableId;

    private String junctionTableName;

    private String junctionDomainColumn;

    private String junctionRangeColumn;
}
