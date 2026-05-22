package com.hik.osp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDetail {
    private String tableName;
    private String tableComment;
    private List<ColumnInfo> columns;
}
