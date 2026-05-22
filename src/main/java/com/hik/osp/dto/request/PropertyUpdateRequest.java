package com.hik.osp.dto.request;

import com.hik.osp.enums.DataType;
import com.hik.osp.enums.PropertyType;
import com.hik.osp.enums.RelationType;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PropertyUpdateRequest {
    private String name;
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
