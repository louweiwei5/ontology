package com.hik.osp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DslQueryResponse {
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private List<Map<String, Object>> data;
    private long total;
    private String message;
    private String sql;
}
