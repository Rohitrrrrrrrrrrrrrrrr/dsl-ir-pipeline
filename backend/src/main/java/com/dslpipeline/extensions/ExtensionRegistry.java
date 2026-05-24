package com.dslpipeline.extensions;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merged extension registry: {@code MergedRegistry = CoreRegistry ⊕ ProjectRegistry}.
 *
 * Core functions are registered under both their namespaced name
 * ({@code date.calculateAge}) and a bare alias ({@code calculateAge}) so authors
 * may use either style. Project functions are namespaced only and may not
 * shadow a core namespace.
 *
 * @author Nikunj Malik
 */
@Component
public class ExtensionRegistry {

    private final Map<String, ExtensionFunction> byName = new LinkedHashMap<>();
    private final List<ExtensionFunction> canonical = new ArrayList<>();

    public ExtensionRegistry() {
        for (ExtensionFunction f : CoreExtensions.all()) {
            registerCore(f);
        }
        for (ExtensionFunction f : ProjectExtensions.acmePack()) {
            registerProject(f);
        }
    }

    private void registerCore(ExtensionFunction f) {
        canonical.add(f);
        byName.put(f.getName(), f);                     // namespaced: date.calculateAge
        String bare = bareName(f.getName());
        // bare alias only if it does not clash with an already-registered name
        byName.putIfAbsent(bare, f);                    // bare: calculateAge
    }

    private void registerProject(ExtensionFunction f) {
        String pack = f.getPack();
        if (isCoreNamespace(pack)) {
            throw new IllegalStateException("Project pack may not shadow core namespace: " + pack);
        }
        canonical.add(f);
        byName.put(f.getName(), f);                     // namespaced only
    }

    private static boolean isCoreNamespace(String pack) {
        return pack.equals("date") || pack.equals("collection")
                || pack.equals("string") || pack.equals("logic");
    }

    private static String bareName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    /** Custom (DB-backed) functions registered at runtime by CustomFunctionService. */
    private final Map<String, ExtensionFunction> customByName = new LinkedHashMap<>();

    /** Resolve a function by namespaced or bare name; null if unknown. */
    public ExtensionFunction resolve(String name) {
        if (name == null) return null;
        return byName.get(name.trim());
    }

    public boolean has(String name) {
        return resolve(name) != null;
    }

    /**
     * Register a DB-backed custom function. Custom functions are namespaced and
     * may not shadow a core function.
     */
    public void registerCustomFunction(ExtensionFunction f) {
        if (isCoreNamespace(f.getPack())) {
            throw new IllegalArgumentException(
                    "Custom function may not use core namespace: " + f.getPack());
        }
        byName.put(f.getName(), f);
        customByName.put(f.getName(), f);
    }

    /** Drop all custom functions (called before a reload). */
    public void clearCustomFunctions() {
        for (String n : customByName.keySet()) byName.remove(n);
        customByName.clear();
    }

    public List<ExtensionFunction> customFunctions() {
        return new ArrayList<>(customByName.values());
    }

    /** All registered functions — core packs + project pack + DB-backed custom. */
    public List<ExtensionFunction> all() {
        List<ExtensionFunction> out = new ArrayList<>(canonical);
        out.addAll(customByName.values());
        return out;
    }

    /** All human-readable signatures, e.g. for the Workbench palette. */
    public List<String> signatures() {
        List<String> out = new ArrayList<>();
        for (ExtensionFunction f : all()) out.add(f.signature());
        return out;
    }
}
