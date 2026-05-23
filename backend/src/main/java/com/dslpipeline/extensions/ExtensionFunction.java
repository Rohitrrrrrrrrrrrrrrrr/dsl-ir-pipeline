package com.dslpipeline.extensions;

import java.util.List;

/**
 * A single registered extension function — metadata plus a deterministic
 * implementation.
 *
 * Metadata (arg types, return type, determinism) drives:
 *   - DSL validation  (function whitelist + arg type/arity checks)
 *   - type checking   (return type resolution for nested terms)
 *   - the trace       (every call is recorded as an audit step)
 *
 * @author Nikunj Malik
 */
public class ExtensionFunction {

    /** Implementation contract — pure, deterministic, no I/O. */
    @FunctionalInterface
    public interface Impl {
        Object apply(List<Object> args);
    }

    private final String name;          // namespaced, e.g. "date.calculateAge"
    private final String pack;          // owning pack, e.g. "date" / "collection" / "acme"
    private final List<String> argTypes;// e.g. ["date","date"]
    private final String returnType;    // e.g. "number" / "boolean" / "date" / "string" / "decimal"
    private final boolean deterministic;
    private final boolean variadic;     // last arg type repeats
    private final String description;
    private final Impl impl;

    public ExtensionFunction(String name, String pack, List<String> argTypes, String returnType,
                             boolean deterministic, boolean variadic, String description, Impl impl) {
        this.name = name;
        this.pack = pack;
        this.argTypes = argTypes;
        this.returnType = returnType;
        this.deterministic = deterministic;
        this.variadic = variadic;
        this.description = description;
        this.impl = impl;
    }

    public String getName() { return name; }
    public String getPack() { return pack; }
    public List<String> getArgTypes() { return argTypes; }
    public String getReturnType() { return returnType; }
    public boolean isDeterministic() { return deterministic; }
    public boolean isVariadic() { return variadic; }
    public String getDescription() { return description; }

    public int arity() { return argTypes.size(); }

    public Object invoke(List<Object> args) {
        return impl.apply(args);
    }

    /** Human-readable signature, e.g. {@code date.calculateAge(date, date) -> number}. */
    public String signature() {
        return name + "(" + String.join(", ", argTypes) + (variadic ? "..." : "") + ") -> " + returnType;
    }
}
