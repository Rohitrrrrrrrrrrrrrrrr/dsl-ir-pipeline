package com.dslpipeline.model.ir;

import com.dslpipeline.numeric.NumericProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical Intermediate Representation (IR) — the execution contract.
 *
 * The IR is language-neutral, fully type-resolved, and deterministic. Paths are
 * normalised to segment arrays, operator aliases are collapsed, literals are
 * tagged ({num},{dec},{str},{bool},{set}), and a numeric profile pins decimal
 * behaviour so TS / Java / .NET / Go / Rust runtimes produce identical results.
 *
 * "No DSL/AST processing at runtime" — the interpreter consumes IR only.
 *
 * @author Nikunj Malik
 */
public class CanonicalIR {

    private String irVersion = "ir_v1";
    private String kind = "rule";
    private String id;
    private int priority = 100;
    private String version = "1.0.0";
    private Instant compiledAt;
    private boolean haltOnViolation = false;
    private String effectiveFrom;
    private String effectiveTo;

    /** Deterministic numeric profile. */
    private NumericProfile numericProfile = new NumericProfile();

    /** Normalised condition tree. */
    private Node when;

    /** Actions run when {@code when} passes. */
    private List<Node> then = new ArrayList<>();

    /** Actions run when {@code when} fails (ELSE branch). */
    private List<Node> elseThen = new ArrayList<>();

    /** Every schema path referenced anywhere in the IR. */
    private List<String> referencedPaths = new ArrayList<>();

    /** Every extension function referenced anywhere in the IR. */
    private List<String> referencedFunctions = new ArrayList<>();

    /** Provenance: source NL, DSL hash, compiler version, prompt hash, author. */
    private Map<String, Object> provenance = new LinkedHashMap<>();

    public CanonicalIR() {}

    public String getIrVersion() { return irVersion; }
    public void setIrVersion(String irVersion) { this.irVersion = irVersion; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Instant getCompiledAt() { return compiledAt; }
    public void setCompiledAt(Instant compiledAt) { this.compiledAt = compiledAt; }
    public boolean isHaltOnViolation() { return haltOnViolation; }
    public void setHaltOnViolation(boolean haltOnViolation) { this.haltOnViolation = haltOnViolation; }
    public String getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public String getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
    public NumericProfile getNumericProfile() { return numericProfile; }
    public void setNumericProfile(NumericProfile numericProfile) { this.numericProfile = numericProfile; }
    public Node getWhen() { return when; }
    public void setWhen(Node when) { this.when = when; }
    public List<Node> getThen() { return then; }
    public void setThen(List<Node> then) { this.then = then; }
    public List<Node> getElseThen() { return elseThen; }
    public void setElseThen(List<Node> elseThen) { this.elseThen = elseThen; }
    public List<String> getReferencedPaths() { return referencedPaths; }
    public void setReferencedPaths(List<String> referencedPaths) { this.referencedPaths = referencedPaths; }
    public List<String> getReferencedFunctions() { return referencedFunctions; }
    public void setReferencedFunctions(List<String> referencedFunctions) { this.referencedFunctions = referencedFunctions; }
    public Map<String, Object> getProvenance() { return provenance; }
    public void setProvenance(Map<String, Object> provenance) { this.provenance = provenance; }

    /**
     * Generic IR node.
     *
     * Operators ({@code op}):
     *   boolean   : AND, OR, NOT, MINUS
     *   quantifier: EXISTS, FORALL, COUNT_WHERE
     *   special   : RULE_REF, DECISION_TABLE
     *   comparison: &gt; &gt;= &lt; &lt;= == != in "not in" "is missing" "is present"
     *   term      : PATH, LIT, CALL
     *   action    : RAISE, WARN, PUSH, ENSURE
     */
    public static class Node {
        private String op;
        private List<Node> args;
        private Node lhs;
        private Node rhs;
        private List<String> path;
        private Object literal;
        private String literalKind;            // num | dec | str | bool | set | date | null
        private Map<String, Object> extra = new LinkedHashMap<>();

        public Node() {}
        public Node(String op) { this.op = op; }

        public String getOp() { return op; }
        public void setOp(String op) { this.op = op; }
        public List<Node> getArgs() { return args; }
        public void setArgs(List<Node> args) { this.args = args; }
        public Node getLhs() { return lhs; }
        public void setLhs(Node lhs) { this.lhs = lhs; }
        public Node getRhs() { return rhs; }
        public void setRhs(Node rhs) { this.rhs = rhs; }
        public List<String> getPath() { return path; }
        public void setPath(List<String> path) { this.path = path; }
        public Object getLiteral() { return literal; }
        public void setLiteral(Object literal) { this.literal = literal; }
        public String getLiteralKind() { return literalKind; }
        public void setLiteralKind(String literalKind) { this.literalKind = literalKind; }
        public Map<String, Object> getExtra() { return extra; }
        public void setExtra(Map<String, Object> extra) { this.extra = extra; }

        // ── factory helpers (keep the IR builder readable) ──

        public static Node path(List<String> segments) {
            Node n = new Node("PATH");
            n.path = segments;
            return n;
        }

        public static Node lit(Object value, String kind) {
            Node n = new Node("LIT");
            n.literal = value;
            n.literalKind = kind;
            return n;
        }

        public static Node call(String fn, List<Node> args) {
            Node n = new Node("CALL");
            n.extra.put("fn", fn);
            n.args = args;
            return n;
        }

        public boolean isTerm() {
            return "PATH".equals(op) || "LIT".equals(op) || "CALL".equals(op);
        }
    }
}
