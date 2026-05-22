package com.hik.osp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hik.osp.entity.OntologyEntity;
import com.hik.osp.entity.PropertyEntity;
import com.hik.osp.enums.DataType;
import com.hik.osp.enums.PropertyType;
import com.hik.osp.enums.RelationType;
import com.hik.osp.exception.ConflictException;
import com.hik.osp.exception.ResourceNotFoundException;
import com.hik.osp.exception.UnprocessableEntityException;
import com.hik.osp.repository.ClassRepository;
import com.hik.osp.repository.OntologyRepository;
import com.hik.osp.repository.PropertyRepository;
import com.hik.osp.util.IriUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyService {

    private final OntologyRepository ontologyRepository;
    private final PropertyRepository propertyRepository;
    private final ClassRepository classRepository;
    private final ObjectMapper objectMapper;

    private OntologyEntity getOntologyOrThrow(String id) {
        return ontologyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ontology", id));
    }

    private PropertyEntity getPropertyOrThrow(String ontologyId, String propId) {
        PropertyEntity prop = propertyRepository.findById(propId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propId));
        if (!prop.getOntologyId().equals(ontologyId)) {
            throw new ResourceNotFoundException("Property", propId);
        }
        return prop;
    }

    public PropertyEntity create(String ontologyId, String name, PropertyType propertyType,
                                  DataType dataType, String domainClassName, String range,
                                  String description, List<Map<String, String>> mappingRules,
                                  RelationType relationType, String junctionTableId,
                                  String junctionTableName, String junctionDomainColumn,
                                  String junctionRangeColumn) {
        OntologyEntity ontology = getOntologyOrThrow(ontologyId);

        // Check duplicate name
        propertyRepository.findByOntologyIdAndName(ontologyId, name).ifPresent(existing -> {
            throw new ConflictException("Property '" + name + "' already exists in this ontology");
        });

        // Resolve domain class — treat empty string as null
        String domainId = null;
        if (domainClassName != null && !domainClassName.isBlank()) {
            domainId = classRepository.findByOntologyIdAndName(ontologyId, domainClassName)
                    .map(e -> e.getId())
                    .orElseThrow(() -> new UnprocessableEntityException(
                            "Domain class '" + domainClassName + "' not found in this ontology"));
        }

        PropertyEntity prop = new PropertyEntity();
        prop.setOntologyId(ontologyId);
        prop.setName(name);
        prop.setFullIri(IriUtils.buildFullIri(ontology.getNamespace(), name));
        prop.setPropertyType(propertyType);
        prop.setRelationType(relationType);
        prop.setDataType(dataType);
        prop.setDomainClassId(domainId);
        prop.setRange(range);
        prop.setDescription(description);
        prop.setJunctionTableId(junctionTableId);
        prop.setJunctionTableName(junctionTableName);
        prop.setJunctionDomainColumn(junctionDomainColumn);
        prop.setJunctionRangeColumn(junctionRangeColumn);
        if (mappingRules != null) {
            try {
                prop.setMappingRules(objectMapper.writeValueAsString(mappingRules));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid mapping rules format");
            }
        }
        return propertyRepository.save(prop);
    }

    public PropertyEntity getById(String ontologyId, String propId) {
        return getPropertyOrThrow(ontologyId, propId);
    }

    public List<PropertyEntity> listAll(String ontologyId) {
        getOntologyOrThrow(ontologyId);
        return propertyRepository.findByOntologyId(ontologyId);
    }

    @Transactional
    public PropertyEntity update(String ontologyId, String propId, String name,
                                  PropertyType propertyType, DataType dataType,
                                  String domainClassName, String range,
                                  String description, List<Map<String, String>> mappingRules,
                                  RelationType relationType, String junctionTableId,
                                  String junctionTableName, String junctionDomainColumn,
                                  String junctionRangeColumn) {
        PropertyEntity prop = getPropertyOrThrow(ontologyId, propId);

        if (name != null && !name.equals(prop.getName())) {
            propertyRepository.findByOntologyIdAndName(ontologyId, name).ifPresent(existing -> {
                if (!existing.getId().equals(propId)) {
                    throw new ConflictException("Property name '" + name + "' already exists in this ontology");
                }
            });
            prop.setName(name);
            prop.setFullIri(IriUtils.buildFullIri(
                    ontologyRepository.findById(ontologyId).map(o -> o.getNamespace()).orElse(""),
                    name));
        }

        if (propertyType != null) prop.setPropertyType(propertyType);
        if (relationType != null) prop.setRelationType(relationType);
        if (dataType != null) prop.setDataType(dataType);
        if (range != null) prop.setRange(range);
        if (description != null) prop.setDescription(description);
        if (junctionTableId != null) prop.setJunctionTableId(junctionTableId);
        if (junctionTableName != null) prop.setJunctionTableName(junctionTableName);
        if (junctionDomainColumn != null) prop.setJunctionDomainColumn(junctionDomainColumn);
        if (junctionRangeColumn != null) prop.setJunctionRangeColumn(junctionRangeColumn);

        if (domainClassName != null && !domainClassName.isBlank()) {
            String domainId = classRepository.findByOntologyIdAndName(ontologyId, domainClassName)
                    .map(e -> e.getId())
                    .orElseThrow(() -> new UnprocessableEntityException(
                            "Domain class '" + domainClassName + "' not found"));
            prop.setDomainClassId(domainId);
        } else if (domainClassName != null) {
            prop.setDomainClassId(null);
        }

        if (mappingRules != null) {
            try {
                prop.setMappingRules(objectMapper.writeValueAsString(mappingRules));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid mapping rules format");
            }
        }

        return propertyRepository.save(prop);
    }

    public void delete(String ontologyId, String propId) {
        PropertyEntity prop = getPropertyOrThrow(ontologyId, propId);
        propertyRepository.delete(prop);
    }
}
