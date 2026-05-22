package com.hik.osp.util;

public class IriUtils {

    private IriUtils() {}

    /**
     * Build the full IRI for an entity within an ontology namespace.
     */
    public static String buildFullIri(String namespace, String name) {
        String ns = namespace == null ? "" : namespace;
        if (!ns.isEmpty() && !ns.endsWith("/") && !ns.endsWith("#")) {
            ns = ns + "#";
        }
        return ns + name;
    }

    /**
     * Extract the local name from a full IRI (after # or last /).
     */
    public static String localName(String iri) {
        if (iri == null) return "";
        if (iri.contains("#")) {
            return iri.substring(iri.lastIndexOf('#') + 1);
        }
        if (iri.contains("/")) {
            String afterSlash = iri.substring(iri.lastIndexOf('/') + 1);
            return afterSlash.isEmpty() ? iri : afterSlash;
        }
        return iri;
    }
}
