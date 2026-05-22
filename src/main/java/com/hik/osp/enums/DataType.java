package com.hik.osp.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DataType {
    STRING("string"),
    INTEGER("integer"),
    FLOAT("float"),
    BOOLEAN("boolean"),
    DATE("date"),
    DATETIME("datetime"),
    TEXT("text");

    private final String value;

    DataType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static DataType fromValue(String value) {
        for (DataType dt : values()) {
            if (dt.value.equals(value)) {
                return dt;
            }
        }
        return null;
    }
}
