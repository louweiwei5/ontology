package com.hik.osp.service;

import com.hik.osp.dto.import_export.*;
import com.hik.osp.enums.DataType;
import com.hik.osp.enums.PropertyType;
import com.hik.osp.util.IriUtils;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.springframework.stereotype.Component;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;

/**
 * OWL/RDF parser using Apache Jena.
 * Replaces the Python rdflib-based owl_parser.py.
 * Parses OWL files (Protégé-compatible) into an OntologyImportRequest structure.
 */
@Component
public class OwlParser {

    private static final Set<String> OWL_NS = Set.of("http://www.w3.org/2002/07/owl#");

    // XSD type → DataType mapping
    private static final Map<String, String> XSD_TYPE_MAP = new LinkedHashMap<>();

    static {
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#string", "string");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#normalizedString", "string");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#token", "string");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#NCName", "string");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#integer", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#int", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#unsignedInt", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#long", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#unsignedLong", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#short", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#unsignedShort", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#byte", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#unsignedByte", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#nonNegativeInteger", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#positiveInteger", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#nonPositiveInteger", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#negativeInteger", "integer");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#float", "float");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#double", "float");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#decimal", "float");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#boolean", "boolean");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#date", "date");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#dateTime", "datetime");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#dateTimeStamp", "datetime");
        XSD_TYPE_MAP.put("http://www.w3.org/2001/XMLSchema#time", "datetime");
        XSD_TYPE_MAP.put(RDFS.Literal.getURI(), "string");
    }

    /**
     * Parse OWL/RDF file content and return an OntologyImportRequest.
     *
     * @param fileContent raw bytes of the OWL file
     * @return OntologyImportRequest compatible with OntologyService.importFromJson()
     * @throws IllegalArgumentException if parsing fails or no usable triples
     */
    public OntologyImportRequest parse(byte[] fileContent) {
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("Empty file");
        }

        // Try parsing with multiple RDF formats
        Model model = null;
        Lang[] formatsToTry = {Lang.RDFXML, Lang.TURTLE, Lang.N3, Lang.JSONLD, Lang.TRIG};
        RiotException lastException = null;

        for (Lang lang : formatsToTry) {
            try {
                model = ModelFactory.createDefaultModel();
                InputStream in = new ByteArrayInputStream(fileContent);
                RDFDataMgr.read(model, in, lang);
                if (model.size() > 0) {
                    break;
                }
                model.close();
                model = null;
            } catch (RiotException e) {
                lastException = e;
                if (model != null) {
                    model.close();
                    model = null;
                }
            }
        }

        // Fallback: try OWL API for OWL/XML format (.owx files)
        if (model == null || model.size() == 0) {
            Model owlApiModel = parseWithOwlApi(fileContent);
            if (owlApiModel != null) {
                if (model != null) model.close();
                model = owlApiModel;
            }
        }

        if (model == null || model.size() == 0) {
            throw new IllegalArgumentException(
                    "Unable to parse file as OWL/RDF. Supported formats: RDF/XML (.owl, .owx, .rdf), Turtle (.ttl), N3, JSON-LD."
                            + (lastException != null ? " Last error: " + lastException.getMessage() : ""));
        }

        try {
            return parseModel(model);
        } finally {
            model.close();
        }
    }

    /**
     * Fallback: parse OWL/XML format using OWL API, then convert to Jena Model for further processing.
     */
    private Model parseWithOwlApi(byte[] fileContent) {
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
                    .setMissingImportHandlingStrategy(org.semanticweb.owlapi.model.MissingImportHandlingStrategy.SILENT);
            manager.setOntologyLoaderConfiguration(config);

            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new ByteArrayInputStream(fileContent));

            // Export as RDF/XML so Jena can parse it
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ontology.saveOntology(new RDFXMLDocumentFormat(), out);

            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, new ByteArrayInputStream(out.toByteArray()), Lang.RDFXML);
            return model;
        } catch (OWLOntologyCreationException | org.semanticweb.owlapi.model.OWLOntologyStorageException | RiotException e) {
            // OWL/XML parsing failed, return null to signal fallback didn't work
            return null;
        }
    }

    private OntologyImportRequest parseModel(Model model) {
        // Collect all statements for iteration
        StmtIterator stmts = model.listStatements();

        // ── 1. Ontology IRI ──
        String ontologyIri = null;
        String namespace;
        String name;

        ResIterator ontSubjects = model.listSubjectsWithProperty(RDF.type, OWL2.Ontology);
        if (ontSubjects.hasNext()) {
            Resource ontRes = ontSubjects.next();
            ontologyIri = ontRes.getURI();
        }

        if (ontologyIri != null) {
            namespace = ontologyIri.replaceAll("/#$", "") + "#";
            name = IriUtils.localName(ontologyIri);
        } else {
            // Fallback: derive from first class IRI
            ResIterator classIt = model.listSubjectsWithProperty(RDF.type, OWL2.Class);
            List<String> classIris = new ArrayList<>();
            while (classIt.hasNext()) {
                Resource cls = classIt.next();
                if (cls.isURIResource()) {
                    classIris.add(cls.getURI());
                }
            }

            if (classIris.isEmpty()) {
                // Try RDFS.Class
                classIt = model.listSubjectsWithProperty(RDF.type, RDFS.Class);
                while (classIt.hasNext()) {
                    Resource cls = classIt.next();
                    if (cls.isURIResource()) {
                        classIris.add(cls.getURI());
                    }
                }
            }

            if (classIris.isEmpty()) {
                throw new IllegalArgumentException("No owl:Class or rdfs:Class declarations found in the file");
            }

            String firstIri = classIris.get(0);
            if (firstIri.contains("#")) {
                namespace = firstIri.substring(0, firstIri.lastIndexOf('#') + 1);
            } else {
                namespace = firstIri.substring(0, firstIri.lastIndexOf('/') + 1);
            }
            name = IriUtils.localName(namespace.replaceAll("/#$", ""));
        }

        // ── 2. Collect classes ──
        Map<String, ClassEntry> classMap = new LinkedHashMap<>();

        // OWL.Class
        ExtendedIterator<Triple> classTriples = model.getGraph().find(null, RDF.type.asNode(), OWL2.Class.asNode());
        while (classTriples.hasNext()) {
            Triple t = classTriples.next();
            if (!t.getSubject().isURI()) continue;
            String iri = t.getSubject().getURI();
            if (!classMap.containsKey(iri)) {
                classMap.put(iri, extractClassInfo(model, iri));
            }
        }

        // RDFS.Class (only those not already captured as OWL.Class)
        classTriples = model.getGraph().find(null, RDF.type.asNode(), RDFS.Class.asNode());
        while (classTriples.hasNext()) {
            Triple t = classTriples.next();
            if (!t.getSubject().isURI()) continue;
            String iri = t.getSubject().getURI();
            if (!classMap.containsKey(iri)) {
                classMap.put(iri, extractClassInfo(model, iri));
            }
        }

        if (classMap.isEmpty()) {
            throw new IllegalArgumentException("No classes found in the OWL file");
        }

        // Build name → IRI lookup
        Map<String, String> classNameToIri = new HashMap<>();
        for (Map.Entry<String, ClassEntry> entry : classMap.entrySet()) {
            classNameToIri.put(entry.getValue().name, entry.getKey());
        }

        // Build class result list with parent references (by name)
        List<ClassImportItem> classes = new ArrayList<>();
        Map<String, String> iriToName = new HashMap<>();
        classMap.forEach((iri, entry) -> iriToName.put(iri, entry.name));

        for (Map.Entry<String, ClassEntry> entry : classMap.entrySet()) {
            String clsIri = entry.getKey();
            ClassEntry ce = entry.getValue();

            String parentName = null;
            StmtIterator superIt = model.listStatements(
                    model.createResource(clsIri), RDFS.subClassOf, (RDFNode) null);
            while (superIt.hasNext()) {
                Statement s = superIt.next();
                RDFNode obj = s.getObject();
                if (obj.isURIResource()) {
                    String parentIri = obj.asResource().getURI();
                    if (!OWL_NS.stream().anyMatch(parentIri::startsWith)) {
                        String pName = iriToName.get(parentIri);
                        if (pName != null) {
                            parentName = pName;
                            break;
                        }
                    }
                }
            }

            classes.add(ClassImportItem.builder()
                    .name(ce.name)
                    .description(ce.description)
                    .parentClass(parentName)
                    .build());
        }

        // ── 3. Data properties ──
        List<PropertyImportItem> properties = new ArrayList<>();

        ResIterator dpIt = model.listSubjectsWithProperty(RDF.type, OWL2.DatatypeProperty);
        while (dpIt.hasNext()) {
            Resource prop = dpIt.next();
            if (!prop.isURIResource()) continue;
            String iri = prop.getURI();

            String propName = getLabelOrLocalName(model, prop, iri);
            String comment = getComment(model, prop);

            // Domain
            String domainName = getDomainClassName(model, prop, iriToName);

            // Range (XSD type)
            String dataType = null;
            StmtIterator rangeIt = model.listStatements(prop, RDFS.range, (RDFNode) null);
            while (rangeIt.hasNext()) {
                Statement s = rangeIt.next();
                if (s.getObject().isURIResource()) {
                    String rangeUri = s.getObject().asResource().getURI();
                    String mapped = XSD_TYPE_MAP.get(rangeUri);
                    if (mapped != null) {
                        dataType = mapped;
                        break;
                    }
                }
            }

            properties.add(PropertyImportItem.builder()
                    .name(propName)
                    .propertyType(PropertyType.DATA)
                    .dataType(dataType != null ? DataType.fromValue(dataType) : null)
                    .domainClass(domainName)
                    .range(null)
                    .description(comment)
                    .build());
        }

        // ── 4. Object properties ──
        ResIterator opIt = model.listSubjectsWithProperty(RDF.type, OWL2.ObjectProperty);
        while (opIt.hasNext()) {
            Resource prop = opIt.next();
            if (!prop.isURIResource()) continue;
            String iri = prop.getURI();

            // Skip built-in OWL properties
            if (OWL_NS.stream().anyMatch(iri::startsWith)) continue;

            String propName = getLabelOrLocalName(model, prop, iri);
            String comment = getComment(model, prop);

            // Domain
            String domainName = getDomainClassName(model, prop, iriToName);

            // Range (target class)
            String rangeClass = null;
            StmtIterator rangeIt = model.listStatements(prop, RDFS.range, (RDFNode) null);
            while (rangeIt.hasNext()) {
                Statement s = rangeIt.next();
                if (s.getObject().isURIResource()) {
                    String rangeUri = s.getObject().asResource().getURI();
                    if (iriToName.containsKey(rangeUri)) {
                        rangeClass = iriToName.get(rangeUri);
                        break;
                    }
                }
            }

            properties.add(PropertyImportItem.builder()
                    .name(propName)
                    .propertyType(PropertyType.OBJECT)
                    .dataType(null)
                    .domainClass(domainName)
                    .range(rangeClass)
                    .description(comment)
                    .build());
        }

        return OntologyImportRequest.builder()
                .name(name)
                .namespace(namespace)
                .description(ontologyIri != null
                        ? "Imported from OWL: " + ontologyIri
                        : "Imported from OWL")
                .version("1.0.0")
                .classes(classes)
                .properties(properties)
                .build();
    }

    private ClassEntry extractClassInfo(Model model, String iri) {
        Resource res = model.createResource(iri);
        String label = getLabelOrLocalName(model, res, iri);
        String comment = getComment(model, res);
        return new ClassEntry(label, comment);
    }

    private String getLabelOrLocalName(Model model, Resource res, String iri) {
        StmtIterator it = model.listStatements(res, RDFS.label, (RDFNode) null);
        try {
            if (it.hasNext()) {
                RDFNode label = it.next().getObject();
                if (label.isLiteral()) {
                    return label.asLiteral().getString();
                }
            }
        } finally {
            it.close();
        }
        return IriUtils.localName(iri);
    }

    private String getComment(Model model, Resource res) {
        StmtIterator it = model.listStatements(res, RDFS.comment, (RDFNode) null);
        try {
            if (it.hasNext()) {
                RDFNode comment = it.next().getObject();
                if (comment.isLiteral()) {
                    return comment.asLiteral().getString();
                }
            }
        } finally {
            it.close();
        }
        return null;
    }

    private String getDomainClassName(Model model, Resource prop, Map<String, String> iriToName) {
        StmtIterator domainIt = model.listStatements(prop, RDFS.domain, (RDFNode) null);
        try {
            while (domainIt.hasNext()) {
                Statement s = domainIt.next();
                if (s.getObject().isURIResource()) {
                    String dIri = s.getObject().asResource().getURI();
                    String name = iriToName.get(dIri);
                    if (name != null) return name;
                }
            }
        } finally {
            domainIt.close();
        }
        return null;
    }

    private record ClassEntry(String name, String description) {}
}
