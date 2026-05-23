package com.dslpipeline.term;

import java.math.BigDecimal;
import java.util.List;

/**
 * A parsed condition term — the smallest typed unit inside a leaf condition.
 *
 * A term is one of:
 *   - {@link PathTerm}    — a schema dot-path, e.g. {@code customer.age}
 *   - {@link LiteralTerm} — a typed literal, e.g. {@code 18}, {@code "AU"}, {@code dec("100.00")}
 *   - {@link CallTerm}    — an extension-function call, e.g. {@code calculateAge(a, b)}
 *
 * Terms can nest: a CallTerm's arguments are themselves Terms.
 *
 * @author Nikunj Malik
 */
public sealed interface Term permits Term.PathTerm, Term.LiteralTerm, Term.CallTerm {

    /** A schema dot-path. */
    record PathTerm(List<String> segments) implements Term {
        public String dotted() { return String.join(".", segments); }
    }

    /**
     * A typed literal.
     * kind ∈ { "num", "dec", "str", "bool", "date", "null" }
     */
    record LiteralTerm(Object value, String kind) implements Term {
        public static LiteralTerm num(long v)        { return new LiteralTerm(v, "num"); }
        public static LiteralTerm dec(BigDecimal v)  { return new LiteralTerm(v, "dec"); }
        public static LiteralTerm str(String v)      { return new LiteralTerm(v, "str"); }
        public static LiteralTerm bool(boolean v)    { return new LiteralTerm(v, "bool"); }
        public static LiteralTerm date(String iso)   { return new LiteralTerm(iso, "date"); }
        public static LiteralTerm nul()              { return new LiteralTerm(null, "null"); }
    }

    /** An extension-function call (possibly namespaced, e.g. {@code date.isBefore}). */
    record CallTerm(String name, List<Term> args) implements Term {}
}
