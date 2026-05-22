package com.hik.osp.enums.converters;

import com.hik.osp.enums.RelationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RelationTypeConverter implements AttributeConverter<RelationType, String> {

    @Override
    public String convertToDatabaseColumn(RelationType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public RelationType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RelationType.fromValue(dbData);
    }
}
