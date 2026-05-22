package com.hik.osp.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnInfo {
    private String columnName;
    private String columnType;
    @JsonProperty("is_nullable")
    private boolean nullable;
    private String columnComment;
    @JsonProperty("is_primary_key")
    private boolean primaryKey;
}
