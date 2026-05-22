package com.hik.osp.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class DslQueryRequest {
    private OntologyInfo ontology;
    private QueryBody query;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OntologyInfo {
        private String name;
        private String namespace;
        private String version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryBody {
        private String target;
        @JsonProperty("selection")
        private List<Object> selection;       // String | SelectItem
        private FilterGroup filter;
        private Pagination pagination;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectItem {
        private String relation;
        @JsonProperty("nested_fields")
        private List<Object> nestedFields;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterGroup {
        private String logic = "AND";
        private List<Object> conditions;      // FilterCondition | FilterGroup
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterCondition {
        private List<String> path;
        private String field;
        private String operator;
        private Object value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {
        private Integer page;
        private Integer size;
        private Integer offset;
        private Integer limit;
    }
}
