package com.dslpipeline.executor;

import com.dslpipeline.extensions.ExtensionFunction;
import com.dslpipeline.extensions.ExtensionRegistry;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.numeric.DecimalMath;
import com.dslpipeline.numeric.NumericProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime interpreter — executes a Canonical IR against a payload.
 *
 * Pure and deterministic: decimal-safe arithmetic, UTC date-only semantics,
 * stable evaluation order. Produces a result, a structured "Why / Why-Not"
 * explanation, a flat trace and a golden trace for parity testing.
 *
 * "No DSL/AST processing at runtime" — only IR is consumed here.
 *
 * @author Nikunj Malik
 */
@Component
public class IrExecutor {

    private final ExtensionRegistry registry;

    public IrExecutor(ExtensionRegistry registry) {
        this.registry = registry;
    }

    public ExecutionResult execute(CanonicalIR ir, Map<String, Object> payload) {
        ExecutionResult r = new ExecutionResult();
        r.ruleId = ir.getId();
        r.irVersion = ir.getIrVersion();
        r.ruleVersion = ir.getVersion();

        NumericProfile profile = ir.getNumericProfile() != null
                ? ir.getNumericProfile() : new NumericProfile();

        Map<String, Object> ctx = deepCopy(payload == null ? Map.of() : payload);

        // ── evaluate WHEN ──
        EvalNode whenTrace = new EvalNode();
        whenTrace.label = "WHEN";
        boolean met = evalCondition(ir.getWhen(), ctx, whenTrace);
        r.conditionTrace = whenTrace;
        r.conditionsMet = met;
        r.branchTaken = met ? "THEN" : "ELSE";

        // ── run the appropriate branch ──
        List<CanonicalIR.Node> branch = met ? ir.getThen() : ir.getElseThen();
        if (branch != null) {
            for (CanonicalIR.Node action : branch) {
                applyAction(action, ctx, r, profile);
            }
        }

        r.outputPayload = ctx;
        r.passed = r.errors.isEmpty();
        r.explanation = explain(ir, r);
        r.goldenTrace = buildGoldenTrace(ir, payload, r);
        return r;
    }

    // ─────────────────────────── condition evaluation ───────────────────────────

    private boolean evalCondition(CanonicalIR.Node node, Map<String, Object> ctx, EvalNode trace) {
        if (node == null) { trace.result = true; trace.detail = "no condition (vacuously true)"; return true; }
        String op = node.getOp() == null ? "" : node.getOp();

        switch (op) {
            case "AND" -> {
                trace.kind = "AND";
                trace.label = "AND";
                boolean all = true;
                for (CanonicalIR.Node a : safe(node.getArgs())) {
                    EvalNode child = new EvalNode();
                    trace.children.add(child);
                    if (!evalCondition(a, ctx, child)) all = false;
                }
                trace.result = all;
                return all;
            }
            case "OR" -> {
                trace.kind = "OR";
                trace.label = "OR";
                boolean any = false;
                for (CanonicalIR.Node a : safe(node.getArgs())) {
                    EvalNode child = new EvalNode();
                    trace.children.add(child);
                    if (evalCondition(a, ctx, child)) any = true;
                }
                trace.result = any;
                return any;
            }
            case "NOT" -> {
                trace.kind = "NOT";
                trace.label = "NOT";
                EvalNode child = new EvalNode();
                trace.children.add(child);
                boolean inner = !safe(node.getArgs()).isEmpty()
                        && evalCondition(node.getArgs().get(0), ctx, child);
                trace.result = !inner;
                return !inner;
            }
            case "MINUS" -> {
                trace.kind = "MINUS";
                trace.label = "MINUS";
                List<CanonicalIR.Node> args = safe(node.getArgs());
                if (args.isEmpty()) { trace.result = false; return false; }
                EvalNode inc = new EvalNode();
                trace.children.add(inc);
                boolean include = evalCondition(args.get(0), ctx, inc);
                boolean excluded = false;
                for (int i = 1; i < args.size(); i++) {
                    EvalNode ex = new EvalNode();
                    trace.children.add(ex);
                    if (evalCondition(args.get(i), ctx, ex)) excluded = true;
                }
                trace.result = include && !excluded;
                return trace.result;
            }
            case "EXISTS", "FORALL" -> {
                return evalQuantifier(op, node, ctx, trace);
            }
            case "COUNT_WHERE" -> {
                return evalCountWhere(node, ctx, trace);
            }
            case "RULE_REF" -> {
                trace.kind = "RULE_REF";
                trace.label = "ruleRef " + node.getExtra().get("ruleId");
                trace.detail = "rule references are treated as satisfied (deprecated chaining)";
                trace.result = true;
                return true;
            }
            case "DECISION_TABLE" -> {
                trace.kind = "DECISION_TABLE";
                trace.label = "decisionTable";
                trace.detail = "decision tables evaluate on the action side; treated as true here";
                trace.result = true;
                return true;
            }
            default -> {
                return evalComparison(node, ctx, trace);
            }
        }
    }

    private boolean evalComparison(CanonicalIR.Node node, Map<String, Object> ctx, EvalNode trace) {
        trace.kind = "LEAF";
        String op = node.getOp();
        Object left = evalTerm(node.getLhs(), ctx);
        Object right = node.getRhs() == null ? null : evalTerm(node.getRhs(), ctx);

        boolean res;
        switch (op) {
            case "is missing" -> res = left == null;
            case "is present" -> res = left != null;
            case "==" -> res = valuesEqual(left, right);
            case "!=" -> res = !valuesEqual(left, right);
            case "in" -> res = right instanceof List<?> l
                    && l.stream().anyMatch(v -> valuesEqual(left, v));
            case "not in" -> res = right instanceof List<?> l
                    && l.stream().noneMatch(v -> valuesEqual(left, v));
            case ">", ">=", "<", "<=" -> {
                Integer cmp = compareValues(left, right);
                res = cmp != null && switch (op) {
                    case ">" -> cmp > 0;
                    case ">=" -> cmp >= 0;
                    case "<" -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    default -> false;
                };
            }
            default -> res = false;
        }
        trace.label = termLabel(node.getLhs()) + " " + op
                + (right == null ? "" : " " + render(right));
        trace.detail = "lhs=" + render(left) + (right == null ? "" : ", rhs=" + render(right));
        trace.result = res;
        return res;
    }

    private boolean evalQuantifier(String op, CanonicalIR.Node node,
                                   Map<String, Object> ctx, EvalNode trace) {
        trace.kind = op;
        Object coll = readPath(ctx, node.getPath());
        String alias = String.valueOf(node.getExtra().get("as"));
        trace.label = op + " over " + path(node.getPath()) + " as " + alias;
        if (!(coll instanceof List<?> list)) {
            trace.detail = "collection is missing or not a list";
            trace.result = op.equals("FORALL");   // vacuous truth for FORALL
            return trace.result;
        }
        CanonicalIR.Node cond = safe(node.getArgs()).isEmpty() ? null : node.getArgs().get(0);
        boolean result = op.equals("FORALL");
        for (Object item : list) {
            Map<String, Object> scoped = new LinkedHashMap<>(ctx);
            if (alias != null) scoped.put(alias, item);
            EvalNode child = new EvalNode();
            trace.children.add(child);
            boolean itemRes = cond != null && evalCondition(cond, scoped, child);
            if (op.equals("EXISTS") && itemRes) { result = true; }
            if (op.equals("FORALL") && !itemRes) { result = false; }
        }
        trace.result = result;
        return result;
    }

    private boolean evalCountWhere(CanonicalIR.Node node, Map<String, Object> ctx, EvalNode trace) {
        trace.kind = "COUNT_WHERE";
        Object coll = readPath(ctx, node.getPath());
        String alias = String.valueOf(node.getExtra().get("as"));
        String cmpOp = String.valueOf(node.getExtra().get("compareOp"));
        BigDecimal threshold = DecimalMath.toDecimal(node.getExtra().get("value"));
        long count = 0;
        if (coll instanceof List<?> list) {
            CanonicalIR.Node cond = safe(node.getArgs()).isEmpty() ? null : node.getArgs().get(0);
            for (Object item : list) {
                Map<String, Object> scoped = new LinkedHashMap<>(ctx);
                if (alias != null) scoped.put(alias, item);
                EvalNode child = new EvalNode();
                trace.children.add(child);
                if (cond != null && evalCondition(cond, scoped, child)) count++;
            }
        }
        Integer cmp = threshold == null ? null
                : Integer.signum(BigDecimal.valueOf(count).compareTo(threshold));
        boolean res = cmp != null && switch (cmpOp) {
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            default -> false;
        };
        trace.label = "COUNT_WHERE " + path(node.getPath()) + " " + cmpOp + " "
                + node.getExtra().get("value");
        trace.detail = "matched count = " + count;
        trace.result = res;
        return res;
    }

    // ─────────────────────────── term evaluation ───────────────────────────

    private Object evalTerm(CanonicalIR.Node node, Map<String, Object> ctx) {
        if (node == null) return null;
        String op = node.getOp() == null ? "" : node.getOp();
        return switch (op) {
            case "PATH" -> readPath(ctx, node.getPath());
            case "LIT" -> literalValue(node);
            case "CALL" -> {
                String fnName = String.valueOf(node.getExtra().get("fn"));
                ExtensionFunction fn = registry.resolve(fnName);
                if (fn == null) {
                    throw new ExecutionException("unknown function at runtime: " + fnName + "()");
                }
                List<Object> args = new ArrayList<>();
                for (CanonicalIR.Node a : safe(node.getArgs())) {
                    args.add(evalTerm(a, ctx));
                }
                yield fn.invoke(args);
            }
            default -> throw new ExecutionException("not a term node: op=" + op);
        };
    }

    private Object literalValue(CanonicalIR.Node node) {
        Object v = node.getLiteral();
        String kind = node.getLiteralKind();
        if ("dec".equals(kind)) return DecimalMath.toDecimal(v);
        return v;
    }

    // ─────────────────────────── actions ───────────────────────────

    @SuppressWarnings("unchecked")
    private void applyAction(CanonicalIR.Node node, Map<String, Object> ctx,
                             ExecutionResult r, NumericProfile profile) {
        String op = node.getOp();
        switch (op) {
            case "RAISE" -> {
                Map<String, Object> raise = asMap(node.getExtra().get("raise"));
                ExecutionResult.Outcome e = new ExecutionResult.Outcome();
                e.code = str(raise.get("code"));
                e.message = str(raise.get("message"));
                r.errors.add(e);
                r.firedActions.add("RAISE " + e.code);
                r.trace.add("RAISE " + e.code + ": " + e.message);
            }
            case "WARN" -> {
                Map<String, Object> warn = asMap(node.getExtra().get("warn"));
                ExecutionResult.Outcome w = new ExecutionResult.Outcome();
                w.code = str(warn.get("code"));
                w.message = str(warn.get("message"));
                r.warnings.add(w);
                r.firedActions.add("WARN " + w.code);
                r.trace.add("WARN " + w.code + ": " + w.message);
            }
            case "ENSURE" -> {
                Object value = evalTerm(node.getRhs(), ctx);
                Object rounded = roundIfNumeric(value, profile, node.getPath());
                writePath(ctx, node.getPath(), rounded);
                r.firedActions.add("ENSURE " + path(node.getPath()));
                r.trace.add("ENSURE " + path(node.getPath()) + " <- " + render(rounded));
            }
            case "PUSH" -> {
                Object value = node.getExtra().get("value");
                Object existing = readPath(ctx, node.getPath());
                List<Object> list = existing instanceof List<?> e
                        ? new ArrayList<>((List<Object>) e) : new ArrayList<>();
                list.add(value);
                writePath(ctx, node.getPath(), list);
                r.firedActions.add("PUSH " + path(node.getPath()));
                r.trace.add("PUSH " + path(node.getPath()) + " += " + render(value));
            }
            default -> r.trace.add("unknown action op: " + op);
        }
    }

    private Object roundIfNumeric(Object value, NumericProfile profile, List<String> path) {
        BigDecimal d = (value instanceof BigDecimal || value instanceof Double || value instanceof Float)
                ? DecimalMath.toDecimal(value) : null;
        if (d == null) return value;
        return DecimalMath.quantize(d, profile.getDefaultScale(), profile);
    }

    // ─────────────────────────── comparison helpers ───────────────────────────

    private boolean valuesEqual(Object a, Object b) {
        if (Objects.equals(a, b)) return true;
        if (a == null || b == null) return false;
        Integer cmp = compareValues(a, b);
        if (cmp != null) return cmp == 0;
        return a.toString().equals(b.toString());
    }

    /** Compare two values: numeric first, then ISO-date, then string. Null if not comparable. */
    private Integer compareValues(Object a, Object b) {
        if (a == null || b == null) return null;
        BigDecimal da = DecimalMath.toDecimal(a);
        BigDecimal db = DecimalMath.toDecimal(b);
        if (da != null && db != null) return Integer.signum(da.compareTo(db));
        LocalDate la = asDate(a);
        LocalDate lb = asDate(b);
        if (la != null && lb != null) return Integer.signum(la.compareTo(lb));
        return Integer.signum(a.toString().compareTo(b.toString()));
    }

    private LocalDate asDate(Object o) {
        if (o instanceof LocalDate ld) return ld;
        if (o == null) return null;
        String s = o.toString().trim();
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    // ─────────────────────────── path utilities ───────────────────────────

    @SuppressWarnings("unchecked")
    private Object readPath(Map<String, Object> ctx, List<String> path) {
        if (path == null || path.isEmpty()) return null;
        Object cur = ctx;
        for (String seg : path) {
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = ((Map<String, Object>) m).get(seg);
            if (cur == null) return null;
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private void writePath(Map<String, Object> ctx, List<String> path, Object value) {
        if (path == null || path.isEmpty()) return;
        Map<String, Object> cur = ctx;
        for (int i = 0; i < path.size() - 1; i++) {
            Object next = cur.get(path.get(i));
            if (next instanceof Map<?, ?> m) {
                cur = (Map<String, Object>) m;
            } else {
                Map<String, Object> nm = new LinkedHashMap<>();
                cur.put(path.get(i), nm);
                cur = nm;
            }
        }
        cur.put(path.get(path.size() - 1), value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m) out.put(e.getKey(), deepCopy((Map<String, Object>) m));
            else if (v instanceof List<?> l) out.put(e.getKey(), new ArrayList<>(l));
            else out.put(e.getKey(), v);
        }
        return out;
    }

    // ─────────────────────────── explainability ───────────────────────────

    private String explain(CanonicalIR ir, ExecutionResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule '").append(ir.getId()).append("' — ");
        if (r.conditionsMet) {
            sb.append("conditions were MET, so the THEN branch ran. ");
        } else {
            sb.append("conditions were NOT met, so the ELSE branch ran. ");
        }
        List<String> leaves = new ArrayList<>();
        collectLeafExplanations(r.conditionTrace, leaves);
        if (!leaves.isEmpty()) {
            sb.append("Evaluation: ").append(String.join("; ", leaves)).append(". ");
        }
        if (!r.errors.isEmpty()) {
            List<String> codes = new ArrayList<>();
            for (ExecutionResult.Outcome e : r.errors) codes.add(e.code);
            sb.append("Errors raised: ").append(String.join(", ", codes)).append(". ");
        }
        if (!r.warnings.isEmpty()) {
            List<String> codes = new ArrayList<>();
            for (ExecutionResult.Outcome w : r.warnings) codes.add(w.code);
            sb.append("Warnings: ").append(String.join(", ", codes)).append(". ");
        }
        sb.append(r.passed
                ? "Outcome: PASSED (no blocking errors)."
                : "Outcome: FAILED (one or more errors).");
        return sb.toString();
    }

    private void collectLeafExplanations(EvalNode n, List<String> out) {
        if (n == null) return;
        if ("LEAF".equals(n.kind) || "EXISTS".equals(n.kind) || "FORALL".equals(n.kind)
                || "COUNT_WHERE".equals(n.kind)) {
            out.add(n.label + " → " + n.result);
        }
        for (EvalNode c : n.children) collectLeafExplanations(c, out);
    }

    private Map<String, Object> buildGoldenTrace(CanonicalIR ir, Map<String, Object> input,
                                                 ExecutionResult r) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("ruleId", ir.getId());
        g.put("irVersion", ir.getIrVersion());
        g.put("input", input);
        g.put("conditionsMet", r.conditionsMet);
        g.put("branch", r.branchTaken);
        g.put("firedActions", r.firedActions);
        List<String> errCodes = new ArrayList<>();
        for (ExecutionResult.Outcome e : r.errors) errCodes.add(e.code);
        g.put("errors", errCodes);
        g.put("passed", r.passed);
        g.put("output", r.outputPayload);
        return g;
    }

    // ─────────────────────────── small helpers ───────────────────────────

    private static <T> List<T> safe(List<T> l) { return l == null ? List.of() : l; }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private String termLabel(CanonicalIR.Node t) {
        if (t == null) return "?";
        return switch (t.getOp() == null ? "" : t.getOp()) {
            case "PATH" -> path(t.getPath());
            case "LIT" -> render(t.getLiteral());
            case "CALL" -> t.getExtra().get("fn") + "(...)";
            default -> "?";
        };
    }

    private static String path(List<String> p) {
        return p == null ? "?" : String.join(".", p);
    }

    private static String render(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return "\"" + s + "\"";
        return v.toString();
    }

    // ─────────────────────────── result types ───────────────────────────

    /** Thrown for unrecoverable runtime errors (unknown function, malformed IR). */
    public static final class ExecutionException extends RuntimeException {
        public ExecutionException(String message) { super(message); }
    }

    public static class ExecutionResult {
        public String ruleId;
        public String irVersion;
        public String ruleVersion;
        public boolean passed;
        public boolean conditionsMet;
        public String branchTaken;
        public List<Outcome> errors = new ArrayList<>();
        public List<Outcome> warnings = new ArrayList<>();
        public List<String> firedActions = new ArrayList<>();
        public List<String> trace = new ArrayList<>();
        public EvalNode conditionTrace;
        public String explanation;
        public Map<String, Object> outputPayload;
        public Map<String, Object> goldenTrace;

        public static class Outcome {
            public String code;
            public String message;
        }
    }

    /** A node in the condition-evaluation tree (drives the Why / Why-Not view). */
    public static class EvalNode {
        public String kind = "";
        public String label = "";
        public Boolean result;
        public String detail;
        public List<EvalNode> children = new ArrayList<>();
    }
}
