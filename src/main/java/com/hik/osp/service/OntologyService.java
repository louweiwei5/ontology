package com.hik.osp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hik.osp.dto.import_export.*;
import com.hik.osp.dto.response.GraphData;
import com.hik.osp.entity.ClassEntity;
import com.hik.osp.entity.OntologyEntity;
import com.hik.osp.entity.PropertyEntity;
import com.hik.osp.enums.DataType;
import com.hik.osp.enums.PropertyType;
import com.hik.osp.exception.ConflictException;
import com.hik.osp.exception.ResourceNotFoundException;
import com.hik.osp.repository.ClassRepository;
import com.hik.osp.repository.OntologyRepository;
import com.hik.osp.repository.TableImportRepository;
import com.hik.osp.repository.PropertyRepository;
import com.hik.osp.util.IriUtils;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OntologyService {

    private final OntologyRepository ontologyRepository;
    private final ClassRepository classRepository;
    private final PropertyRepository propertyRepository;
    private final TableImportRepository tableImportRepository;
    private final ObjectMapper objectMapper;

    // ── XSD type mapping ──

    private static final Map<String, String> DATA_TYPE_TO_XSD = Map.ofEntries(
            Map.entry("string", "xsd:string"),
            Map.entry("integer", "xsd:integer"),
            Map.entry("float", "xsd:float"),
            Map.entry("boolean", "xsd:boolean"),
            Map.entry("date", "xsd:date"),
            Map.entry("datetime", "xsd:dateTime"),
            Map.entry("text", "xsd:string")
    );

    private String mapDataTypeToXsd(String dt) {
        return DATA_TYPE_TO_XSD.getOrDefault(dt, "xsd:string");
    }

    private static final Map<String, Resource> XSD_TYPE_RESOURCE = Map.ofEntries(
            Map.entry("string", XSD.xstring),
            Map.entry("integer", XSD.integer),
            Map.entry("float", XSD.xfloat),
            Map.entry("boolean", XSD.xboolean),
            Map.entry("date", XSD.date),
            Map.entry("datetime", XSD.dateTime),
            Map.entry("text", XSD.xstring)
    );

    // ── Internal helpers ──

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

    private PropertyEntity getPropertyOrThrow(String ontologyId, String propId) {
        PropertyEntity prop = propertyRepository.findById(propId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propId));
        if (!prop.getOntologyId().equals(ontologyId)) {
            throw new ResourceNotFoundException("Property", propId);
        }
        return prop;
    }

    private String classNameToId(String ontologyId, String name) {
        return classRepository.findByOntologyIdAndName(ontologyId, name)
                .map(ClassEntity::getId)
                .orElse(null);
    }

    private String classIdToName(String classId) {
        if (classId == null) return null;
        return classRepository.findById(classId)
                .map(ClassEntity::getName)
                .orElse(null);
    }

    // ── Ontology CRUD ──

    public OntologyEntity create(String name, String namespace, String description, String version) {
        if (ontologyRepository.existsByName(name)) {
            throw new ConflictException("Ontology '" + name + "' already exists");
        }
        OntologyEntity entity = new OntologyEntity();
        entity.setName(name);
        entity.setNamespace(namespace != null ? namespace : "http://example.org/" + name);
        entity.setDescription(description);
        entity.setVersion(version != null ? version : "1.0.0");
        return ontologyRepository.save(entity);
    }

    public OntologyEntity getById(String id) {
        return getOntologyOrThrow(id);
    }

    public List<Map<String, Object>> listAll() {
        List<OntologyEntity> all = ontologyRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (OntologyEntity o : all) {
            long classCount = classRepository.countByOntologyId(o.getId());
            long propCount = propertyRepository.countByOntologyId(o.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", o.getId());
            item.put("name", o.getName());
            item.put("namespace", o.getNamespace());
            item.put("description", o.getDescription());
            item.put("version", o.getVersion());
            item.put("class_count", classCount);
            item.put("property_count", propCount);
            item.put("created_at", o.getCreatedAt());
            item.put("updated_at", o.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    public OntologyEntity update(String id, Map<String, Object> updates) {
        OntologyEntity ontology = getOntologyOrThrow(id);

        if (updates.containsKey("name")) {
            String newName = (String) updates.get("name");
            if (!newName.equals(ontology.getName())) {
                ontologyRepository.findByName(newName).ifPresent(existing -> {
                    throw new ConflictException("Ontology name '" + newName + "' already exists");
                });
            }
            ontology.setName(newName);
        }
        if (updates.containsKey("namespace")) {
            ontology.setNamespace((String) updates.get("namespace"));
        }
        if (updates.containsKey("description")) {
            ontology.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("version")) {
            ontology.setVersion((String) updates.get("version"));
        }

        return ontologyRepository.save(ontology);
    }

    public void delete(String id) {
        OntologyEntity ontology = getOntologyOrThrow(id);
        // Delete table imports, properties (FK on domain_class_id), then cascade handles classes
        tableImportRepository.deleteByOntologyId(id);
        propertyRepository.deleteByOntologyId(id);
        ontologyRepository.delete(ontology);
    }

    // ── Export ──

    public OntologyExportResponse exportJson(String id) {
        OntologyEntity ontology = getOntologyOrThrow(id);
        List<ClassEntity> classes = classRepository.findByOntologyId(id);
        List<PropertyEntity> properties = propertyRepository.findByOntologyId(id);

        List<ClassExportItem> classItems = classes.stream()
                .map(c -> ClassExportItem.builder()
                        .name(c.getName())
                        .description(c.getDescription())
                        .parentClass(classIdToName(c.getParentClassId()))
                        .build())
                .collect(Collectors.toList());

        List<PropertyExportItem> propItems = properties.stream()
                .map(p -> PropertyExportItem.builder()
                        .name(p.getName())
                        .propertyType(p.getPropertyType())
                        .dataType(p.getDataType())
                        .domainClass(classIdToName(p.getDomainClassId()))
                        .range(p.getRange())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toList());

        return OntologyExportResponse.builder()
                .name(ontology.getName())
                .namespace(ontology.getNamespace())
                .description(ontology.getDescription())
                .version(ontology.getVersion())
                .classes(classItems)
                .properties(propItems)
                .build();
    }

    // ── Graph visualization ──

    public GraphData getGraph(String id) {
        getOntologyOrThrow(id);
        List<ClassEntity> classes = classRepository.findByOntologyId(id);
        List<PropertyEntity> properties = propertyRepository.findByOntologyId(id);

        Map<String, String> classIdToName = new HashMap<>();
        Map<String, List<String>> dataPropsByClass = new HashMap<>();
        List<GraphData.GraphNode> nodes = new ArrayList<>();
        List<GraphData.GraphEdge> edges = new ArrayList<>();

        for (ClassEntity c : classes) {
            classIdToName.put(c.getId(), c.getName());
            dataPropsByClass.put(c.getId(), new ArrayList<>());
            nodes.add(GraphData.GraphNode.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .type("class")
                    .build());
        }

        // Hierarchy edges
        for (ClassEntity c : classes) {
            if (c.getParentClassId() != null) {
                edges.add(GraphData.GraphEdge.builder()
                        .source(c.getId())
                        .target(c.getParentClassId())
                        .label("")
                        .type("hierarchy")
                        .build());
            }
        }

        // Property edges + data properties
        for (PropertyEntity p : properties) {
            if (p.getPropertyType() == PropertyType.OBJECT && p.getDomainClassId() != null && p.getRange() != null) {
                String targetId = classes.stream()
                        .filter(c -> c.getName().equals(p.getRange()))
                        .map(ClassEntity::getId)
                        .findFirst().orElse(null);
                if (targetId != null) {
                    edges.add(GraphData.GraphEdge.builder()
                            .source(p.getDomainClassId())
                            .target(targetId)
                            .label(p.getName())
                            .type("property")
                            .build());
                }
            } else if (p.getPropertyType() == PropertyType.DATA && p.getDomainClassId() != null) {
                dataPropsByClass.computeIfAbsent(p.getDomainClassId(), k -> new ArrayList<>())
                        .add(p.getName() + (p.getDataType() != null ? ": " + p.getDataType().getValue() : ""));
            }
        }

        // Attach data properties to nodes
        for (GraphData.GraphNode node : nodes) {
            List<String> props = dataPropsByClass.get(node.getId());
            if (props != null && !props.isEmpty()) {
                node.setDataProperties(props);
            }
        }

        return GraphData.builder().nodes(nodes).edges(edges).build();
    }

    // ── TBox (3 formats) ──

    public String getTbox(String id) {
        OntologyEntity ontology = getOntologyOrThrow(id);
        List<ClassEntity> classes = classRepository.findByOntologyId(id);
        List<PropertyEntity> properties = propertyRepository.findByOntologyId(id);

        // Build hierarchy
        Map<String, List<ClassEntity>> childrenMap = new HashMap<>();
        for (ClassEntity c : classes) {
            if (c.getParentClassId() != null) {
                childrenMap.computeIfAbsent(c.getParentClassId(), k -> new ArrayList<>()).add(c);
            }
        }

        // Group properties by domain class
        Map<String, List<PropertyEntity>> dataPropsByClass = new HashMap<>();
        Map<String, List<PropertyEntity>> objPropsByClass = new HashMap<>();
        for (PropertyEntity p : properties) {
            if (p.getPropertyType() == PropertyType.DATA) {
                dataPropsByClass.computeIfAbsent(p.getDomainClassId(), k -> new ArrayList<>()).add(p);
            } else {
                objPropsByClass.computeIfAbsent(p.getDomainClassId(), k -> new ArrayList<>()).add(p);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Ontology: ").append(ontology.getName()).append("\n");
        if (ontology.getDescription() != null) {
            sb.append("> ").append(ontology.getDescription()).append("\n");
        }
        sb.append("- **Version:** ").append(ontology.getVersion()).append("\n");
        sb.append("- **Namespace:** ").append(ontology.getNamespace()).append("\n");
        sb.append("- **Classes:** ").append(classes.size()).append("\n");
        sb.append("- **Properties:** ").append(properties.size()).append("\n\n");

        List<ClassEntity> roots = classes.stream()
                .filter(c -> c.getParentClassId() == null)
                .collect(Collectors.toList());

        for (ClassEntity root : roots) {
            writeClassMarkdown(sb, root, 0, childrenMap, dataPropsByClass, objPropsByClass);
        }

        // Object Property Details table
        sb.append("\n## Object Property Details\n\n");
        sb.append("| Property | Domain | Range | Mapping Rules | Description |\n");
        sb.append("|----------|--------|-------|---------------|-------------|\n");
        List<PropertyEntity> objOnly = properties.stream()
                .filter(p -> p.getPropertyType() == PropertyType.OBJECT)
                .collect(Collectors.toList());
        if (objOnly.isEmpty()) {
            sb.append("| *(none)* | | | | |\n");
        } else {
            for (PropertyEntity p : objOnly) {
                String dname = classIdToName(p.getDomainClassId());
                if (dname == null) dname = "(global)";
                String mstr = "";
                if (p.getMappingRules() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> rules = objectMapper.readValue(p.getMappingRules(), List.class);
                        mstr = rules.stream()
                                .map(r -> r.get("domain_property") + " = " + r.get("range_property"))
                                .collect(Collectors.joining("; "));
                    } catch (Exception e) {
                        mstr = "(invalid)";
                    }
                }
                String desc = p.getDescription() != null ? p.getDescription() : "";
                sb.append("| ").append(p.getName()).append(" | ").append(dname)
                        .append(" | ").append(p.getRange() != null ? p.getRange() : "-")
                        .append(" | ").append(mstr).append(" | ").append(desc).append(" |\n");
            }
        }

        return sb.toString();
    }

    private void writeClassMarkdown(StringBuilder sb, ClassEntity cls, int indent,
                                     Map<String, List<ClassEntity>> childrenMap,
                                     Map<String, List<PropertyEntity>> dataPropsByClass,
                                     Map<String, List<PropertyEntity>> objPropsByClass) {
        String prefix = "  ".repeat(indent);
        sb.append(prefix).append("- **").append(cls.getName()).append("**\n");
        if (cls.getDescription() != null) {
            sb.append(prefix).append("  - Description: ").append(cls.getDescription()).append("\n");
        }

        List<PropertyEntity> dps = dataPropsByClass.getOrDefault(cls.getId(), List.of());
        if (!dps.isEmpty()) {
            sb.append(prefix).append("  - Data Properties:\n");
            for (PropertyEntity p : dps) {
                String dt = p.getDataType() != null ? ": " + p.getDataType().getValue() : "";
                String desc = p.getDescription() != null ? " — " + p.getDescription() : "";
                sb.append(prefix).append("    - `").append(p.getName()).append(dt).append("`").append(desc).append("\n");
            }
        }

        for (PropertyEntity p : objPropsByClass.getOrDefault(cls.getId(), List.of())) {
            String mappingStr = "";
            if (p.getMappingRules() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> rules = objectMapper.readValue(p.getMappingRules(), List.class);
                    mappingStr = rules.stream()
                            .map(r -> r.get("domain_property") + " = " + r.get("range_property"))
                            .collect(Collectors.joining("; "));
                    if (!mappingStr.isEmpty()) {
                        mappingStr = " [" + mappingStr + "]";
                    }
                } catch (Exception ignored) {}
            }
            String desc = p.getDescription() != null ? " — " + p.getDescription() : "";
            sb.append(prefix).append("  - Object Property `").append(p.getName())
                    .append("` → **").append(p.getRange()).append("**")
                    .append(mappingStr).append(desc).append("\n");
        }

        for (ClassEntity child : childrenMap.getOrDefault(cls.getId(), List.of())) {
            writeClassMarkdown(sb, child, indent + 1, childrenMap, dataPropsByClass, objPropsByClass);
        }
    }

    public String getTboxManchester(String id) {
        OntologyEntity ontology = getOntologyOrThrow(id);
        List<ClassEntity> classes = classRepository.findByOntologyId(id);
        List<PropertyEntity> properties = propertyRepository.findByOntologyId(id);

        String ns = ontology.getNamespace().replaceAll("/#$", "") + "#";
        Map<String, ClassEntity> classById = new HashMap<>();
        Map<String, List<ClassEntity>> childrenMap = new HashMap<>();
        for (ClassEntity c : classes) {
            classById.put(c.getId(), c);
            if (c.getParentClassId() != null) {
                childrenMap.computeIfAbsent(c.getParentClassId(), k -> new ArrayList<>()).add(c);
            }
        }

        Map<String, List<PropertyEntity>> dataPropsByClass = new HashMap<>();
        Map<String, List<PropertyEntity>> objPropsByClass = new HashMap<>();
        for (PropertyEntity p : properties) {
            if (p.getPropertyType() == PropertyType.DATA) {
                dataPropsByClass.computeIfAbsent(p.getDomainClassId(), k -> new ArrayList<>()).add(p);
            } else {
                objPropsByClass.computeIfAbsent(p.getDomainClassId(), k -> new ArrayList<>()).add(p);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Prefix: : <").append(ns).append(">\n");
        sb.append("Prefix: xsd: <http://www.w3.org/2001/XMLSchema#>\n\n");

        String ontoIri = ontology.getNamespace().replaceAll("/#$", "");
        sb.append("Ontology: <").append(ontoIri).append(">\n");
        if (ontology.getDescription() != null) {
            sb.append("    Annotations: rdfs:comment \"").append(ontology.getDescription()).append("\"\n");
        }
        sb.append("    Version: ").append(ontology.getVersion()).append("\n\n");

        sb.append("# -- Classes --\n");
        List<ClassEntity> roots = classes.stream()
                .filter(c -> c.getParentClassId() == null)
                .collect(Collectors.toList());
        for (ClassEntity root : roots) {
            writeClassManchester(sb, root, 0, classById, childrenMap, dataPropsByClass, objPropsByClass);
        }

        Set<String> seenOps = new HashSet<>();
        List<PropertyEntity> objOnly = properties.stream()
                .filter(p -> p.getPropertyType() == PropertyType.OBJECT)
                .collect(Collectors.toList());
        if (!objOnly.isEmpty()) {
            sb.append("\n# -- Object Properties --\n");
            for (PropertyEntity p : objOnly) {
                if (seenOps.contains(p.getName())) continue;
                seenOps.add(p.getName());
                sb.append("ObjectProperty: :").append(p.getName()).append("\n");
                if (p.getDescription() != null) {
                    sb.append("    Annotations: rdfs:comment \"").append(p.getDescription()).append("\"\n");
                }
            }
        }

        return sb.toString();
    }

    private void writeClassManchester(StringBuilder sb, ClassEntity cls, int depth,
                                       Map<String, ClassEntity> classById,
                                       Map<String, List<ClassEntity>> childrenMap,
                                       Map<String, List<PropertyEntity>> dataPropsByClass,
                                       Map<String, List<PropertyEntity>> objPropsByClass) {
        String indent = "    ".repeat(depth);
        sb.append(indent).append("Class: :").append(cls.getName()).append("\n");
        if (cls.getDescription() != null) {
            sb.append(indent).append("    Annotations: rdfs:comment \"").append(cls.getDescription()).append("\"\n");
        }
        if (cls.getParentClassId() != null && classById.containsKey(cls.getParentClassId())) {
            String parentName = classById.get(cls.getParentClassId()).getName();
            sb.append(indent).append("    SubClassOf: :").append(parentName).append("\n");
        } else {
            sb.append(indent).append("    SubClassOf: owl:Thing\n");
        }

        for (PropertyEntity dp : dataPropsByClass.getOrDefault(cls.getId(), List.of())) {
            String dt = dp.getDataType() != null ? dp.getDataType().getValue() : "string";
            String xsdType = mapDataTypeToXsd(dt);
            sb.append(indent).append("    DataProperty: :").append(dp.getName()).append("\n");
            if (dp.getDescription() != null) {
                sb.append(indent).append("        Annotations: rdfs:comment \"").append(dp.getDescription()).append("\"\n");
            }
            sb.append(indent).append("        Domain: :").append(cls.getName()).append("\n");
            sb.append(indent).append("        Range: ").append(xsdType).append("\n");
        }

        for (PropertyEntity op : objPropsByClass.getOrDefault(cls.getId(), List.of())) {
            sb.append(indent).append("    ObjectProperty: :").append(op.getName()).append("\n");
            if (op.getDescription() != null) {
                sb.append(indent).append("        Annotations: rdfs:comment \"").append(op.getDescription()).append("\"\n");
            }
            sb.append(indent).append("        Domain: :").append(cls.getName()).append("\n");
            if (op.getRange() != null) {
                sb.append(indent).append("        Range: :").append(op.getRange()).append("\n");
            }
        }

        for (ClassEntity child : childrenMap.getOrDefault(cls.getId(), List.of())) {
            writeClassManchester(sb, child, depth + 1, classById, childrenMap, dataPropsByClass, objPropsByClass);
        }
    }

    public Map<String, Object> getTboxJson(String id) {
        OntologyEntity ontology = getOntologyOrThrow(id);
        List<ClassEntity> classes = classRepository.findByOntologyId(id);
        List<PropertyEntity> properties = propertyRepository.findByOntologyId(id);

        // Build id→name map for source_class resolution
        Map<String, String> classNameById = new HashMap<>();
        for (ClassEntity c : classes) {
            classNameById.put(c.getId(), c.getName());
        }

        Map<String, List<Map<String, Object>>> dataPropsByClass = new HashMap<>();
        List<Map<String, Object>> relationshipsList = new ArrayList<>();

        for (PropertyEntity p : properties) {
            if (p.getPropertyType() == PropertyType.DATA) {
                Map<String, Object> pMap = new LinkedHashMap<>();
                pMap.put("name", p.getName());
                pMap.put("data_type", p.getDataType() != null ? p.getDataType().getValue() : "string");
                pMap.put("description", p.getDescription());
                dataPropsByClass.computeIfAbsent(p.getDomainClassId(), k -> new ArrayList<>()).add(pMap);
            } else {
                Map<String, Object> rel = new LinkedHashMap<>();
                rel.put("name", p.getName());
                rel.put("relation_type", p.getRelationType() != null ? p.getRelationType().getValue() : null);
                rel.put("source_class", classNameById.get(p.getDomainClassId()));
                rel.put("target_class", p.getRange());
                rel.put("description", p.getDescription());
                if (p.getMappingRules() != null) {
                    try {
                        rel.put("mapping_rules", objectMapper.readValue(p.getMappingRules(), List.class));
                    } catch (Exception ignored) {}
                }
                relationshipsList.add(rel);
            }
        }

        List<Map<String, Object>> classList = new ArrayList<>();
        for (ClassEntity c : classes) {
            Map<String, Object> cMap = new LinkedHashMap<>();
            cMap.put("name", c.getName());
            cMap.put("description", c.getDescription());
            cMap.put("parent", classIdToName(c.getParentClassId()));
            cMap.put("data_properties", dataPropsByClass.getOrDefault(c.getId(), List.of()));
            classList.add(cMap);
        }

        Map<String, Object> ontoMap = new LinkedHashMap<>();
        ontoMap.put("name", ontology.getName());
        ontoMap.put("namespace", ontology.getNamespace());
        ontoMap.put("version", ontology.getVersion());
        ontoMap.put("description", ontology.getDescription());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ontology", ontoMap);
        result.put("classes", classList);
        result.put("relationships", relationshipsList);
        return result;
    }

    // ── OWL Export (Apache Jena) ──

    public String exportOwl(String id, String format) {
        OntologyEntity ontology = getOntologyOrThrow(id);
        List<ClassEntity> classes = classRepository.findByOntologyId(id);
        List<PropertyEntity> properties = propertyRepository.findByOntologyId(id);

        String nsIri = ontology.getNamespace().replaceAll("/#$", "") + "#";
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("", nsIri);
        model.setNsPrefix("owl", OWL2.getURI());
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("xsd", XSD.getURI());

        Resource ontResource = model.createResource(ontology.getNamespace().replaceAll("/#$", ""));
        ontResource.addProperty(RDF.type, OWL2.Ontology);
        if (ontology.getDescription() != null) {
            ontResource.addProperty(RDFS.comment, ontology.getDescription());
        }
        ontResource.addProperty(OWL2.versionInfo, ontology.getVersion());

        // Class IRIs
        Map<String, Resource> classResources = new HashMap<>();
        for (ClassEntity cls : classes) {
            Resource clsRes = model.createResource(nsIri + cls.getName());
            clsRes.addProperty(RDF.type, OWL2.Class);
            clsRes.addProperty(RDFS.label, cls.getName());
            if (cls.getDescription() != null) {
                clsRes.addProperty(RDFS.comment, cls.getDescription());
            }
            classResources.put(cls.getId(), clsRes);
        }

        // SubClassOf
        for (ClassEntity cls : classes) {
            if (cls.getParentClassId() != null && classResources.containsKey(cls.getParentClassId())) {
                classResources.get(cls.getId()).addProperty(RDFS.subClassOf, classResources.get(cls.getParentClassId()));
            }
        }

        // Properties
        for (PropertyEntity p : properties) {
            Resource propRes = model.createResource(nsIri + p.getName());
            propRes.addProperty(RDFS.label, p.getName());
            if (p.getDescription() != null) {
                propRes.addProperty(RDFS.comment, p.getDescription());
            }

            if (p.getPropertyType() == PropertyType.DATA) {
                propRes.addProperty(RDF.type, OWL2.DatatypeProperty);
                if (p.getDomainClassId() != null && classResources.containsKey(p.getDomainClassId())) {
                    propRes.addProperty(RDFS.domain, classResources.get(p.getDomainClassId()));
                }
                if (p.getDataType() != null) {
                    Resource xsdType = XSD_TYPE_RESOURCE.get(p.getDataType().getValue());
                    if (xsdType != null) {
                        propRes.addProperty(RDFS.range, xsdType);
                    }
                }
            } else {
                propRes.addProperty(RDF.type, OWL2.ObjectProperty);
                if (p.getDomainClassId() != null && classResources.containsKey(p.getDomainClassId())) {
                    propRes.addProperty(RDFS.domain, classResources.get(p.getDomainClassId()));
                }
                if (p.getRange() != null) {
                    propRes.addProperty(RDFS.range, model.createResource(nsIri + p.getRange()));
                }
            }
        }

        if ("turtle".equals(format)) {
            StringWriter turtleSw = new StringWriter();
            model.write(turtleSw, "TURTLE");
            return turtleSw.toString();
        }
        StringWriter xmlSw = new StringWriter();
        model.write(xmlSw, "RDF/XML-ABBREV");
        return xmlSw.toString();
    }

    // ── JSON Import ──

    @Transactional
    public OntologyEntity importFromJson(OntologyImportRequest data) {
        if (ontologyRepository.existsByName(data.getName())) {
            throw new ConflictException("Ontology '" + data.getName() + "' already exists");
        }

        OntologyEntity ontology = new OntologyEntity();
        ontology.setName(data.getName());
        ontology.setNamespace(data.getNamespace() != null ? data.getNamespace() : "http://example.org/" + data.getName());
        ontology.setDescription(data.getDescription());
        ontology.setVersion(data.getVersion() != null ? data.getVersion() : "1.0.0");
        ontology = ontologyRepository.save(ontology);

        String ns = ontology.getNamespace();

        // First pass: create classes without parents
        Map<String, ClassEntity> classMap = new HashMap<>();
        for (ClassImportItem clsData : data.getClasses()) {
            ClassEntity cls = new ClassEntity();
            cls.setOntologyId(ontology.getId());
            cls.setName(clsData.getName());
            cls.setFullIri(IriUtils.buildFullIri(ns, clsData.getName()));
            cls.setDescription(clsData.getDescription());
            cls = classRepository.save(cls);
            classMap.put(clsData.getName(), cls);
        }

        // Second pass: resolve parent references
        for (ClassImportItem clsData : data.getClasses()) {
            if (clsData.getParentClass() != null) {
                ClassEntity cls = classMap.get(clsData.getName());
                ClassEntity parent = classMap.get(clsData.getParentClass());
                if (cls != null && parent != null) {
                    cls.setParentClassId(parent.getId());
                    classRepository.save(cls);
                }
            }
        }

        // Create properties
        for (PropertyImportItem propData : data.getProperties()) {
            PropertyEntity prop = new PropertyEntity();
            prop.setOntologyId(ontology.getId());
            prop.setName(propData.getName());
            prop.setFullIri(IriUtils.buildFullIri(ns, propData.getName()));
            prop.setPropertyType(propData.getPropertyType());
            prop.setDataType(propData.getDataType());
            if (propData.getDomainClass() != null) {
                ClassEntity domain = classMap.get(propData.getDomainClass());
                if (domain != null) {
                    prop.setDomainClassId(domain.getId());
                }
            }
            prop.setRange(propData.getRange());
            prop.setDescription(propData.getDescription());
            propertyRepository.save(prop);
        }

        return ontology;
    }
}
