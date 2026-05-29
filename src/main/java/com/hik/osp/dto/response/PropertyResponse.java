package com.hik.osp.dto.response;

import com.hik.osp.enums.DataType;
import com.hik.osp.enums.PropertyType;
import com.hik.osp.enums.RelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyResponse {
    private String id;
    private String ontologyId;
    private String name;
    private String fullIri;
    private PropertyType propertyType;
    private RelationType relationType;
    private DataType dataType;
    private String domainClassId;
    private String range;
    private String description;
    private String junctionTableId;
    private String junctionTableName;
    private String junctionDomainColumn;
    private String junctionRangeColumn;
    private List<Map<String, String>> mappingRules;
    private Boolean primaryKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
