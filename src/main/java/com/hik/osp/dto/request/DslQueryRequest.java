package com.hik.osp.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class DslQueryRequest {
    @JsonProperty("ontology_id")
    private String ontologyId;
    private QueryBody query;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryBody {
        private String name;
        private String description;
        private Subject subject;
        private List<Projection> projection;
        private FilterGroup filters;
        private List<Traversal> traversal;
        private List<OrderBy> orderBy;
        private Pagination pagination;
        private Boolean distinct;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subject {
        private String entity;
        private String alias;
        private Object id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Projection {
        private String entity;
        private String property;
        private String alias;
        private String aggregation;
        private String expression;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterGroup {
        private String logic = "AND";
        private List<FilterCondition> conditions;
        private List<FilterGroup> groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterCondition {
        private String entity;
        private String property;
        private String operator;
        private Object value;
        private String valueType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Traversal {
        private String from;
        private String to;
        private String relation;
        private String direction = "OUT";
        private String cardinality = "MANY";
        private boolean optional;
        private FilterGroup filters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBy {
        private String property;
        private String direction = "ASC";
        private String nulls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {
        @JsonProperty("page")
        private Integer page;
        @JsonProperty("pageSize")
        private Integer pageSize;
        private Integer offset;
        private Integer limit;
    }
}
