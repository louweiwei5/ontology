package com.hik.osp.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PropertyType {
    DATA("data"),
    OBJECT("object");

    private final String value;

    PropertyType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PropertyType fromValue(String value) {
        for (PropertyType pt : values()) {
            if (pt.value.equals(value)) {
                return pt;
            }
        }
        throw new IllegalArgumentException("Unknown PropertyType: " + value);
    }
}
