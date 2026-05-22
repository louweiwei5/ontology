package com.hik.osp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMapping {
    private String tableName;
    private String className;
    private String classId;
    private List<ColumnMapping> columns;
    private String tableComment;
}
