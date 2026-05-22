package com.hik.osp.dto.import_export;

import com.hik.osp.enums.DataType;
import com.hik.osp.enums.PropertyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyImportItem {
    private String name;
    private PropertyType propertyType;
    private DataType dataType;
    private String domainClass;
    private String range;
    private String description;
}
