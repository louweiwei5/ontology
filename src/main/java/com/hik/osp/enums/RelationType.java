package com.hik.osp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RelationType {
    ONE_TO_ONE("one-to-one"),
    ONE_TO_MANY("one-to-many"),
    MANY_TO_ONE("many-to-one"),
    MANY_TO_MANY("many-to-many");

    private final String value;

    RelationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RelationType fromValue(String value) {
        for (RelationType rt : values()) {
            if (rt.value.equals(value)) {
                return rt;
            }
        }
        throw new IllegalArgumentException("Unknown RelationType: " + value);
    }
}
