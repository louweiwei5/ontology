package com.hik.osp.enums.converters;

import com.hik.osp.enums.PropertyType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PropertyTypeConverter implements AttributeConverter<PropertyType, String> {

    @Override
    public String convertToDatabaseColumn(PropertyType attribute) {
        if (attribute == null) return null;
        return attribute.getValue();
    }

    @Override
    public PropertyType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return PropertyType.fromValue(dbData);
    }
}
