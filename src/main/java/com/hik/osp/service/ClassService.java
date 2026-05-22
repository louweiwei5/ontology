package com.hik.osp.service;

import com.hik.osp.entity.ClassEntity;
import com.hik.osp.entity.OntologyEntity;
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

@Service
@RequiredArgsConstructor
@Transactional
public class ClassService {

    private final OntologyRepository ontologyRepository;
    private final ClassRepository classRepository;
    private final PropertyRepository propertyRepository;

    private OntologyEntity getOntologyOrThrow(String id) {
        return ontologyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ontology", id));
    }

    private ClassEntity getClassOrThrow(String ontologyId, String classId) {
        ClassEntity cls = classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class", classId));
        if (!cls.getOntologyId().equals(ontologyId)) {
            throw new ResourceNotFoundException("Class", classId);
        }
        return cls;
    }

    public ClassEntity create(String ontologyId, String name, String description, String parentClassName) {
        OntologyEntity ontology = getOntologyOrThrow(ontologyId);

        // Check duplicate name
        classRepository.findByOntologyIdAndName(ontologyId, name).ifPresent(existing -> {
            throw new ConflictException("Class '" + name + "' already exists in this ontology");
        });

        // Resolve parent — treat empty string as null
        String parentId = null;
        if (parentClassName != null && !parentClassName.isBlank()) {
            ClassEntity parent = classRepository.findByOntologyIdAndName(ontologyId, parentClassName)
                    .orElseThrow(() -> new UnprocessableEntityException(
                            "Parent class '" + parentClassName + "' not found in this ontology"));
            parentId = parent.getId();
        }

        ClassEntity cls = new ClassEntity();
        cls.setOntologyId(ontologyId);
        cls.setName(name);
        cls.setFullIri(IriUtils.buildFullIri(ontology.getNamespace(), name));
        cls.setDescription(description);
        cls.setParentClassId(parentId);
        return classRepository.save(cls);
    }

    public ClassEntity getById(String ontologyId, String classId) {
        return getClassOrThrow(ontologyId, classId);
    }

    public List<ClassEntity> listAll(String ontologyId) {
        getOntologyOrThrow(ontologyId);
        return classRepository.findByOntologyId(ontologyId);
    }

    @Transactional
    public ClassEntity update(String ontologyId, String classId, String name, String description, String parentClassName) {
        ClassEntity cls = getClassOrThrow(ontologyId, classId);

        if (name != null && !name.equals(cls.getName())) {
            classRepository.findByOntologyIdAndName(ontologyId, name).ifPresent(existing -> {
                if (!existing.getId().equals(classId)) {
                    throw new ConflictException("Class name '" + name + "' already exists in this ontology");
                }
            });
            cls.setName(name);
            cls.setFullIri(IriUtils.buildFullIri(
                    ontologyRepository.findById(ontologyId).map(o -> o.getNamespace()).orElse(""),
                    name));
        }

        if (description != null) {
            cls.setDescription(description);
        }

        if (parentClassName != null && !parentClassName.isBlank()) {
            ClassEntity parent = classRepository.findByOntologyIdAndName(ontologyId, parentClassName)
                    .orElseThrow(() -> new UnprocessableEntityException(
                            "Parent class '" + parentClassName + "' not found"));
            cls.setParentClassId(parent.getId());
        } else if (parentClassName != null) {
            // explicitly set to null (empty string sent by frontend)
            cls.setParentClassId(null);
        }

        return classRepository.save(cls);
    }

    @Transactional
    public void delete(String ontologyId, String classId) {
        ClassEntity cls = getClassOrThrow(ontologyId, classId);

        // Nullify references from child classes and properties
        classRepository.nullifyParentReferences(classId);
        classRepository.nullifyDomainClassReferences(classId);

        classRepository.delete(cls);
    }
}
