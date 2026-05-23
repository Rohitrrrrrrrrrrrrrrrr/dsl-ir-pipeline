package com.dslpipeline.testengine;

import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.numeric.DecimalMath;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test engine — mechanically generates must-have test scenarios from the IR.
 *
 * For every {@code path OP literal} comparison the generator emits boundary
 * cases (at / just-below / just-above the threshold) plus a fully-satisfying
 * and a fully-violating case. Each scenario is executed so its expected
 * outcome is a deterministic golden value.
 *
 * @author Nikunj Malik
 */
@Component
public class ScenarioGenerator {

    private static final int MAX_SCENARIOS = 25;

    private final IrExecutor executor;

    public ScenarioGenerator(IrExecutor executor) {
        this.executor = executor;
    }

    public List<Scenario> generate(CanonicalIR ir) {
        List<LeafSpec> leaves = new ArrayList<>();
        collectLeaves(ir.getWhen(), leaves);

        List<Scenario> scenarios = new ArrayList<>();

        // 1. fully-satisfying base case
        Map<String, Object> base = basePayload(leaves);
        scenarios.add(run(ir, "all-conditions-satisfied",
                "Every comparison set to a value that satisfies it.", "satisfying", base));

        // 2. per-leaf boundary variants
        for (LeafSpec leaf : leaves) {
            if (scenarios.size() >= MAX_SCENARIOS) break;
            for (BoundaryCase bc : boundaryCases(leaf)) {
                if (scenarios.size() >= MAX_SCENARIOS) break;
                Map<String, Object> p = deepCopy(base);
                writePath(p, leaf.path, bc.value);
                scenarios.add(run(ir, leaf.dotted() + " " + bc.label,
                        "Boundary probe on " + leaf.dotted() + " " + leaf.op + " " + leaf.literal,
                        "boundary", p));
            }
        }

        // 3. a deliberately-violating case (flip the first leaf)
        if (!leaves.isEmpty()) {
            Map<String, Object> v = deepCopy(base);
            LeafSpec first = leaves.get(0);
            Object violating = violatingValue(first);
            if (violating != null) {
                writePath(v, first.path, violating);
                scenarios.add(run(ir, "first-condition-violated",
                        "First comparison set to a violating value.", "violating", v));
            }
        }
        return scenarios;
    }

    private Scenario run(CanonicalIR ir, String name, String desc, String category,
                         Map<String, Object> payload) {
        IrExecutor.ExecutionResult res = executor.execute(ir, payload);
        Scenario s = new Scenario();
        s.name = name;
        s.description = desc;
        s.category = category;
        s.payload = payload;
        s.expectedConditionsMet = res.conditionsMet;
        s.expectedPassed = res.passed;
        s.expectedBranch = res.branchTaken;
        for (IrExecutor.ExecutionResult.Outcome e : res.errors) s.expectedErrorCodes.add(e.code);
        s.goldenTrace = res.goldenTrace;
        return s;
    }

    // ─────────────────────────── leaf collection ───────────────────────────

    private void collectLeaves(CanonicalIR.Node node, List<LeafSpec> out) {
        if (node == null) return;
        String op = node.getOp() == null ? "" : node.getOp();
        switch (op) {
            case "AND", "OR", "NOT", "MINUS" -> {
                if (node.getArgs() != null) {
                    for (CanonicalIR.Node a : node.getArgs()) collectLeaves(a, out);
                }
            }
            case "EXISTS", "FORALL", "COUNT_WHERE", "RULE_REF", "DECISION_TABLE" -> {
                /* quantifiers operate on collections — not boundary-probed in v1 */
            }
            default -> {
                // a comparison leaf: PATH op LIT
                if (node.getLhs() != null && "PATH".equals(node.getLhs().getOp())
                        && node.getRhs() != null && "LIT".equals(node.getRhs().getOp())) {
                    LeafSpec spec = new LeafSpec();
                    spec.path = node.getLhs().getPath();
                    spec.op = op;
                    spec.literal = node.getRhs().getLiteral();
                    spec.literalKind = node.getRhs().getLiteralKind();
                    out.add(spec);
                }
            }
        }
    }

    // ─────────────────────────── value generation ───────────────────────────

    private Map<String, Object> basePayload(List<LeafSpec> leaves) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (LeafSpec leaf : leaves) {
            writePath(p, leaf.path, satisfyingValue(leaf));
        }
        return p;
    }

    private Object satisfyingValue(LeafSpec leaf) {
        BigDecimal v = DecimalMath.toDecimal(leaf.literal);
        if (v != null) {
            return switch (leaf.op) {
                case "<" -> num(v.subtract(BigDecimal.ONE), leaf);
                case "<=" -> num(v, leaf);
                case ">" -> num(v.add(BigDecimal.ONE), leaf);
                case ">=" -> num(v, leaf);
                case "==" -> num(v, leaf);
                case "!=" -> num(v.add(BigDecimal.ONE), leaf);
                default -> num(v, leaf);
            };
        }
        if ("set".equals(leaf.literalKind) && leaf.literal instanceof List<?> l && !l.isEmpty()) {
            return "in".equals(leaf.op) ? l.get(0) : "value-not-in-set-" + System.identityHashCode(leaf);
        }
        if (leaf.literal instanceof Boolean b) {
            return leaf.op.equals("!=") ? !b : b;
        }
        // string literal
        return leaf.op.equals("!=") ? leaf.literal + "_other" : leaf.literal;
    }

    private Object violatingValue(LeafSpec leaf) {
        BigDecimal v = DecimalMath.toDecimal(leaf.literal);
        if (v != null) {
            return switch (leaf.op) {
                case "<" -> num(v.add(BigDecimal.ONE), leaf);
                case "<=" -> num(v.add(BigDecimal.ONE), leaf);
                case ">" -> num(v.subtract(BigDecimal.ONE), leaf);
                case ">=" -> num(v.subtract(BigDecimal.ONE), leaf);
                case "==" -> num(v.add(BigDecimal.ONE), leaf);
                case "!=" -> num(v, leaf);
                default -> num(v, leaf);
            };
        }
        if (leaf.literal instanceof Boolean b) return !b;
        if (leaf.literal != null) return leaf.literal + "_violating";
        return null;
    }

    private List<BoundaryCase> boundaryCases(LeafSpec leaf) {
        List<BoundaryCase> cases = new ArrayList<>();
        BigDecimal v = DecimalMath.toDecimal(leaf.literal);
        if (v != null) {
            cases.add(new BoundaryCase("at-threshold", num(v, leaf)));
            cases.add(new BoundaryCase("just-below", num(v.subtract(BigDecimal.ONE), leaf)));
            cases.add(new BoundaryCase("just-above", num(v.add(BigDecimal.ONE), leaf)));
        }
        return cases;
    }

    private Object num(BigDecimal d, LeafSpec leaf) {
        if ("dec".equals(leaf.literalKind)) return d;
        if (d.scale() <= 0 || d.stripTrailingZeros().scale() <= 0) return d.longValue();
        return d;
    }

    // ─────────────────────────── payload utilities ───────────────────────────

    @SuppressWarnings("unchecked")
    private void writePath(Map<String, Object> root, List<String> path, Object value) {
        if (path == null || path.isEmpty()) return;
        Map<String, Object> cur = root;
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

    // ─────────────────────────── data carriers ───────────────────────────

    private static class LeafSpec {
        List<String> path;
        String op;
        Object literal;
        String literalKind;
        String dotted() { return path == null ? "?" : String.join(".", path); }
    }

    private record BoundaryCase(String label, Object value) {}

    public static class Scenario {
        public String name;
        public String description;
        public String category;        // satisfying | boundary | violating
        public Map<String, Object> payload;
        public boolean expectedConditionsMet;
        public boolean expectedPassed;
        public String expectedBranch;
        public List<String> expectedErrorCodes = new ArrayList<>();
        public Map<String, Object> goldenTrace;
    }
}
