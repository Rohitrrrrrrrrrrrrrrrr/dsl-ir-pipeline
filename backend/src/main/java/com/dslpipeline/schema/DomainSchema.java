package com.dslpipeline.schema;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Domain schema (the DAL anchor) — the typed contract every SL/DSL identifier
 * is validated against.
 *
 * Held as a flat map of dot-path -> type, where type ∈
 * { string, number, integer, decimal, boolean, date, collection, object }.
 *
 * Used by:
 *   - SL linter   (DAL alignment: unknown field / type-operator mismatch)
 *   - type checker (operator/argument type resolution)
 *
 * @author Nikunj Malik
 */
public class DomainSchema {

    private final Map<String, String> fieldTypes = new LinkedHashMap<>();
    private final Set<String> rootEntities = new LinkedHashSet<>();

    public DomainSchema() {}

    /** Build from a flat { "customer.age": "number", ... } map. */
    public static DomainSchema fromFlatMap(Map<String, String> flat) {
        DomainSchema s = new DomainSchema();
        if (flat != null) {
            for (Map.Entry<String, String> e : flat.entrySet()) {
                s.declare(e.getKey(), e.getValue());
            }
        }
        return s;
    }

    public void declare(String path, String type) {
        if (path == null || type == null) return;
        fieldTypes.put(path, type.toLowerCase().trim());
        int dot = path.indexOf('.');
        rootEntities.add(dot > 0 ? path.substring(0, dot) : path);
    }

    public boolean isEmpty() { return fieldTypes.isEmpty(); }

    public boolean hasPath(String path) { return fieldTypes.containsKey(path); }

    /** Type of a declared path, or null if not declared. */
    public String typeOf(String path) { return fieldTypes.get(path); }

    public Set<String> rootEntities() { return rootEntities; }

    public Map<String, String> fieldTypes() { return fieldTypes; }

    /** True when the root entity of a path is known, even if the leaf field is not. */
    public boolean knowsRootOf(String path) {
        if (path == null) return false;
        int dot = path.indexOf('.');
        String root = dot > 0 ? path.substring(0, dot) : path;
        return rootEntities.contains(root);
    }

    /** Whether a declared type is numeric (orderable by arithmetic comparison). */
    public static boolean isNumeric(String type) {
        return "number".equals(type) || "integer".equals(type) || "decimal".equals(type);
    }

    /** Whether an ordered comparison (&lt; &gt; &lt;= &gt;=) is valid for a type. */
    public static boolean isOrderable(String type) {
        return isNumeric(type) || "date".equals(type);
    }
}
