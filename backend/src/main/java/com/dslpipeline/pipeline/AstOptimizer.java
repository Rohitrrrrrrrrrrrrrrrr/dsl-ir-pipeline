package com.dslpipeline.pipeline;

import com.dslpipeline.model.dsl.ConditionNode;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.numeric.DecimalMath;
import com.dslpipeline.term.Term;
import com.dslpipeline.term.TermEvaluator;
import com.dslpipeline.term.TermParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional optimisation pass between AST and IR.
 *
 * Transformations:
 *   - Constant folding   — evaluate function calls whose arguments are all
 *                          literals, e.g. {@code calculateAge("2000-01-01","2020-01-01")} → 20.
 *   - Constant comparison eliminited — fold a fully-constant leaf to TRUE/FALSE.
 *   - Dead-branch elimination — drop TRUE children of AND / FALSE children of OR,
 *                          collapse groups, simplify double negation.
 *
 * TRUE  is represented as an empty AND group (vacuously true).
 * FALSE is represented as an empty OR  group (vacuously false).
 *
 * @author Nikunj Malik
 */
@Component
public class AstOptimizer {

    private final TermEvaluator evaluator;

    public AstOptimizer(TermEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public Result optimize(RuleDSL dsl) {
        Result res = new Result();
        List<ConditionNode> optimized = new ArrayList<>();
        for (ConditionNode c : dsl.getConditions()) {
            optimized.add(opt(c, res));
        }
        dsl.setConditions(optimized);
        return res;
    }

    private ConditionNode opt(ConditionNode node, Result res) {
        return switch (node) {
            case ConditionNode.LeafCondition leaf -> optLeaf(leaf, res);
            case ConditionNode.GroupCondition g -> optGroup(g, res);
            case ConditionNode.NotCondition not -> optNot(not, res);
            default -> node; // quantifiers / tables / refs left as-is
        };
    }

    // ─────────────────────────── leaf folding ───────────────────────────

    private ConditionNode optLeaf(ConditionNode.LeafCondition leaf, Result res) {
        String op = leaf.getOp() == null ? "" : leaf.getOp().toLowerCase();
        if (op.startsWith("is ") || op.equals("ismissing") || op.equals("ispresent")) {
            return leaf; // unary — nothing to fold
        }
        Term leftTerm;
        try {
            leftTerm = TermParser.parse(leaf.getLeft());
        } catch (RuntimeException e) {
            return leaf;
        }
        Object leftConst = null;
        boolean leftIsConst = evaluator.isConstant(leftTerm);
        if (leftIsConst) {
            try {
                leftConst = evaluator.evaluateConstant(leftTerm);
                if (leftTerm instanceof Term.CallTerm) {
                    res.transformations.add("constant-folded " + leaf.getLeft() + " → " + leftConst);
                    leaf.setLeft(literalToText(leftConst));
                }
            } catch (RuntimeException e) {
                return leaf;
            }
        }

        Object right = leaf.getRight();
        boolean rightIsConst = right != null && !(right instanceof List<?>) && isConstScalar(right);

        if (leftIsConst && rightIsConst && !op.equals("in") && !op.equals("not in")) {
            Boolean folded = compareConst(leftConst, op, right);
            if (folded != null) {
                res.transformations.add("evaluated constant comparison "
                        + leftConst + " " + op + " " + right + " → " + folded);
                return folded ? trueSentinel() : falseSentinel();
            }
        }
        return leaf;
    }

    // ─────────────────────────── group / not ───────────────────────────

    private ConditionNode optGroup(ConditionNode.GroupCondition g, Result res) {
        boolean and = "AND".equalsIgnoreCase(g.getOperator());
        List<ConditionNode> kept = new ArrayList<>();
        for (ConditionNode child : g.getConditions()) {
            ConditionNode oc = opt(child, res);
            Boolean konst = constValue(oc);
            if (konst != null) {
                if (and && konst) {
                    res.transformations.add("dead-branch: dropped always-true child of AND");
                    continue;
                }
                if (!and && !konst) {
                    res.transformations.add("dead-branch: dropped always-false child of OR");
                    continue;
                }
                if (and && !konst) {
                    res.transformations.add("dead-branch: AND collapses to FALSE (a child is always false)");
                    return falseSentinel();
                }
                if (!and && konst) {
                    res.transformations.add("dead-branch: OR collapses to TRUE (a child is always true)");
                    return trueSentinel();
                }
            }
            kept.add(oc);
        }
        if (kept.isEmpty()) {
            // empty AND = TRUE, empty OR = FALSE  (preserve sentinel semantics)
            return and ? trueSentinel() : falseSentinel();
        }
        if (kept.size() == 1) {
            res.transformations.add("collapsed single-child " + g.getOperator() + " group");
            return kept.get(0);
        }
        return new ConditionNode.GroupCondition(g.getOperator(), kept);
    }

    private ConditionNode optNot(ConditionNode.NotCondition not, Result res) {
        ConditionNode inner = opt(not.getCondition(), res);
        Boolean konst = constValue(inner);
        if (konst != null) {
            res.transformations.add("simplified NOT of a constant → " + (!konst));
            return konst ? falseSentinel() : trueSentinel();
        }
        if (inner instanceof ConditionNode.NotCondition innerNot) {
            res.transformations.add("eliminated double negation NOT(NOT(x)) → x");
            return innerNot.getCondition();
        }
        return new ConditionNode.NotCondition(inner);
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** TRUE/FALSE if the node is a known constant sentinel; otherwise null. */
    private Boolean constValue(ConditionNode node) {
        if (node instanceof ConditionNode.GroupCondition g
                && (g.getConditions() == null || g.getConditions().isEmpty())) {
            return "AND".equalsIgnoreCase(g.getOperator());
        }
        return null;
    }

    private ConditionNode.GroupCondition trueSentinel() {
        return new ConditionNode.GroupCondition("AND", new ArrayList<>());
    }

    private ConditionNode.GroupCondition falseSentinel() {
        return new ConditionNode.GroupCondition("OR", new ArrayList<>());
    }

    private boolean isConstScalar(Object v) {
        return v instanceof Number || v instanceof Boolean || v instanceof BigDecimal
                || (v instanceof String s && !looksLikePath(s));
    }

    private boolean looksLikePath(String s) {
        // a bare identifier path like "customer.age" is NOT a constant
        return s.matches("[A-Za-z_][\\w]*(\\.[A-Za-z_][\\w]*)+")
                || s.matches("[A-Za-z_][\\w]*");
    }

    private Boolean compareConst(Object left, String op, Object right) {
        switch (op) {
            case "==" -> { return equalsConst(left, right); }
            case "!=" -> { return !equalsConst(left, right); }
            default -> { }
        }
        Integer cmp = DecimalMath.compare(left, right);
        if (cmp == null) return null;
        return switch (op) {
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            default -> null;
        };
    }

    private boolean equalsConst(Object a, Object b) {
        if (a == null || b == null) return a == b;
        Integer cmp = DecimalMath.compare(a, b);
        if (cmp != null) return cmp == 0;
        return a.toString().equals(b.toString());
    }

    private String literalToText(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return "\"" + s + "\"";
        return v.toString();
    }

    public static class Result {
        public List<String> transformations = new ArrayList<>();
        public boolean changed() { return !transformations.isEmpty(); }
    }
}
