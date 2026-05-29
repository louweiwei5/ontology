package com.hik.osp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMapping {
    private String columnName;
    private String propertyName;
    private String dataType;
    private String columnComment;
    private boolean primaryKey;
}
