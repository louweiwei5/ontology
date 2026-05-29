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
public class ChatResponse {
    private String reply;
    private Map<String, Object> dslQuery;
    private QueryResult queryResult;
    private String sessionId;
    private String messageId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryResult {
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private long total;
        private String message;
        private String sql;
        private String dslError;
        private Map<String, String> fieldLabels;
        private Map<String, String> relationTypes;
    }
}
