package com.hik.osp.enums.converters;

import com.hik.osp.enums.DataType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DataTypeConverter implements AttributeConverter<DataType, String> {

    @Override
    public String convertToDatabaseColumn(DataType attribute) {
        if (attribute == null) return null;
        return attribute.getValue();
    }

    @Override
    public DataType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return DataType.fromValue(dbData);
    }
}
