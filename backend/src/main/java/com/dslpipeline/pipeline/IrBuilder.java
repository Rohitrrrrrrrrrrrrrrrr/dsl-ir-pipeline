package com.dslpipeline.pipeline;

import com.dslpipeline.model.dsl.ConditionNode;
import com.dslpipeline.model.dsl.DslAction;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.numeric.NumericProfile;
import com.dslpipeline.term.Term;
import com.dslpipeline.term.TermParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 7 — DSL/AST → Canonical IR (canonicalisation layer).
 *
 * "Taking the AST and converting it into a standard, strict, execution-ready
 * form where every concept has exactly one representation."
 *
 *   - operator aliases collapsed   (ismissing → "is missing")
 *   - paths normalised to segment arrays
 *   - terms parsed into PATH / LIT / CALL nodes
 *   - literals tagged (num / dec / str / bool / set / date)
 *   - numeric profile + provenance attached
 *
 * @author Nikunj Malik
 */
@Component
public class IrBuilder {

    public CanonicalIR build(RuleDSL dsl) {
        CanonicalIR ir = new CanonicalIR();
        ir.setKind("rule");
        ir.setId(dsl.getRuleId());
        ir.setPriority(dsl.getPriority());
        ir.setVersion("1.0.0");
        ir.setCompiledAt(Instant.now());
        ir.setHaltOnViolation(dsl.isHaltOnViolation());
        ir.setEffectiveFrom(dsl.getEffectiveFrom());
        ir.setEffectiveTo(dsl.getEffectiveTo());
        ir.setNumericProfile(new NumericProfile(dsl.getRoundingMode()));

        Ctx ctx = new Ctx();

        // WHEN
        CanonicalIR.Node when;
        if (dsl.getConditions() == null || dsl.getConditions().isEmpty()) {
            when = new CanonicalIR.Node("AND");      // vacuously true
            when.setArgs(new ArrayList<>());
        } else if (dsl.getConditions().size() == 1) {
            when = compileCondition(dsl.getConditions().get(0), ctx);
        } else {
            when = new CanonicalIR.Node("AND");
            List<CanonicalIR.Node> args = new ArrayList<>();
            for (ConditionNode c : dsl.getConditions()) args.add(compileCondition(c, ctx));
            when.setArgs(args);
        }
        ir.setWhen(when);

        // THEN / ELSE
        List<CanonicalIR.Node> then = new ArrayList<>();
        if (dsl.getActions() != null) {
            for (DslAction a : dsl.getActions()) then.add(compileAction(a, ctx));
        }
        ir.setThen(then);

        List<CanonicalIR.Node> elseThen = new ArrayList<>();
        if (dsl.getElseActions() != null) {
            for (DslAction a : dsl.getElseActions()) elseThen.add(compileAction(a, ctx));
        }
        ir.setElseThen(elseThen);

        ir.setReferencedPaths(new ArrayList<>(ctx.paths));
        ir.setReferencedFunctions(new ArrayList<>(ctx.functions));

        // provenance
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("compiler", "dsl-ir-pipeline 2.0.0");
        prov.put("source", "DSL");
        prov.put("dslHash", sha256(dsl.getRuleId() + "|" + dsl.getConditions() + "|"
                + dsl.getActions() + "|" + dsl.getElseActions()));
        if (dsl.getMetadata() != null) {
            prov.put("originalNl", dsl.getMetadata().get("originalNl"));
            prov.put("title", dsl.getMetadata().get("title"));
            prov.put("slStrategy", dsl.getMetadata().get("slStrategy"));
            prov.put("slConfidence", dsl.getMetadata().get("slConfidence"));
            if (dsl.getMetadata().get("promptHash") != null) {
                prov.put("promptHash", dsl.getMetadata().get("promptHash"));
            }
        }
        prov.put("author", "rule-author");
        ir.setProvenance(prov);
        return ir;
    }

    // ─────────────────────────── conditions ───────────────────────────

    private CanonicalIR.Node compileCondition(ConditionNode node, Ctx ctx) {
        return switch (node) {
            case ConditionNode.LeafCondition leaf -> compileLeaf(leaf, ctx);
            case ConditionNode.GroupCondition g -> {
                CanonicalIR.Node n = new CanonicalIR.Node(g.getOperator() == null
                        ? "AND" : g.getOperator().toUpperCase());
                List<CanonicalIR.Node> args = new ArrayList<>();
                if (g.getConditions() != null) {
                    for (ConditionNode c : g.getConditions()) args.add(compileCondition(c, ctx));
                }
                n.setArgs(args);
                yield n;
            }
            case ConditionNode.NotCondition not -> {
                CanonicalIR.Node n = new CanonicalIR.Node("NOT");
                n.setArgs(new ArrayList<>(List.of(compileCondition(not.getCondition(), ctx))));
                yield n;
            }
            case ConditionNode.MinusCondition m -> {
                CanonicalIR.Node n = new CanonicalIR.Node("MINUS");
                List<CanonicalIR.Node> args = new ArrayList<>();
                args.add(compileCondition(m.getInclude(), ctx));
                if (m.getExclude() != null) {
                    for (ConditionNode e : m.getExclude()) args.add(compileCondition(e, ctx));
                }
                n.setArgs(args);
                yield n;
            }
            case ConditionNode.ExistsCondition e ->
                    quantifier("EXISTS", e.getCollection(), e.getAs(), e.getCondition(), ctx);
            case ConditionNode.ForAllCondition f ->
                    quantifier("FORALL", f.getCollection(), f.getAs(), f.getCondition(), ctx);
            case ConditionNode.RuleRefCondition r -> {
                CanonicalIR.Node n = new CanonicalIR.Node("RULE_REF");
                n.getExtra().put("ruleId", r.getRuleId());
                yield n;
            }
            case ConditionNode.CountWhereCondition c -> {
                CanonicalIR.Node n = new CanonicalIR.Node("COUNT_WHERE");
                n.setPath(segments(c.getCollection()));
                ctx.paths.add(c.getCollection());
                n.getExtra().put("as", c.getAs());
                n.getExtra().put("compareOp", c.getOp());
                n.getExtra().put("value", c.getValue());
                n.setArgs(new ArrayList<>(List.of(compileCondition(c.getCondition(), ctx))));
                yield n;
            }
            case ConditionNode.DecisionTableCondition dt -> {
                CanonicalIR.Node n = new CanonicalIR.Node("DECISION_TABLE");
                n.getExtra().put("inputs", dt.getInputs());
                n.getExtra().put("rows", dt.getRows());
                n.getExtra().put("otherwise", dt.getOtherwise());
                if (dt.getInputs() != null) ctx.paths.addAll(dt.getInputs());
                yield n;
            }
        };
    }

    private CanonicalIR.Node quantifier(String op, String collection, String as,
                                        ConditionNode cond, Ctx ctx) {
        CanonicalIR.Node n = new CanonicalIR.Node(op);
        n.setPath(segments(collection));
        ctx.paths.add(collection);
        n.getExtra().put("as", as);
        n.setArgs(new ArrayList<>(List.of(compileCondition(cond, ctx))));
        return n;
    }

    private CanonicalIR.Node compileLeaf(ConditionNode.LeafCondition leaf, Ctx ctx) {
        CanonicalIR.Node n = new CanonicalIR.Node(normaliseOp(leaf.getOp()));
        n.setLhs(compileTermString(leaf.getLeft(), ctx));
        Object right = leaf.getRight();
        if (right != null) {
            n.setRhs(compileRight(right, ctx));
        }
        return n;
    }

    /** Compile a leaf's left-hand text into a term node. */
    private CanonicalIR.Node compileTermString(String text, Ctx ctx) {
        Term t;
        try {
            t = TermParser.parse(text);
        } catch (RuntimeException e) {
            // last resort — treat as a raw path
            return CanonicalIR.Node.path(segments(text));
        }
        return compileTerm(t, ctx);
    }

    private CanonicalIR.Node compileTerm(Term term, Ctx ctx) {
        return switch (term) {
            case Term.PathTerm p -> {
                ctx.paths.add(p.dotted());
                yield CanonicalIR.Node.path(p.segments());
            }
            case Term.LiteralTerm lit -> CanonicalIR.Node.lit(lit.value(), lit.kind());
            case Term.CallTerm call -> {
                ctx.functions.add(call.name());
                List<CanonicalIR.Node> args = new ArrayList<>();
                for (Term a : call.args()) args.add(compileTerm(a, ctx));
                yield CanonicalIR.Node.call(call.name(), args);
            }
        };
    }

    /** Compile a leaf's right-hand value (literal, list, path, or function call). */
    @SuppressWarnings("unchecked")
    private CanonicalIR.Node compileRight(Object right, Ctx ctx) {
        if (right instanceof Number num) {
            if (num instanceof BigDecimal) return CanonicalIR.Node.lit(num, "dec");
            return CanonicalIR.Node.lit(num, "num");
        }
        if (right instanceof Boolean b) {
            return CanonicalIR.Node.lit(b, "bool");
        }
        if (right instanceof List<?> list) {
            return CanonicalIR.Node.lit(new ArrayList<>((List<Object>) list), "set");
        }
        if (right instanceof String s) {
            Term t;
            try {
                t = TermParser.parse(s);
            } catch (RuntimeException e) {
                return CanonicalIR.Node.lit(s, "str");
            }
            // a multi-segment path or a function call is a reference; everything
            // else (bare word) is treated as a string literal
            if (t instanceof Term.CallTerm) {
                return compileTerm(t, ctx);
            }
            if (t instanceof Term.PathTerm pt && pt.segments().size() > 1) {
                return compileTerm(t, ctx);
            }
            if (t instanceof Term.LiteralTerm lit && !"str".equals(lit.kind())) {
                return CanonicalIR.Node.lit(lit.value(), lit.kind());
            }
            return CanonicalIR.Node.lit(s, "str");
        }
        return CanonicalIR.Node.lit(right, "str");
    }

    // ─────────────────────────── actions ───────────────────────────

    private CanonicalIR.Node compileAction(DslAction a, Ctx ctx) {
        CanonicalIR.Node n = new CanonicalIR.Node();
        switch (a) {
            case DslAction.AddErrorAction e -> {
                n.setOp("RAISE");
                Map<String, Object> raise = new LinkedHashMap<>();
                raise.put("code", e.getCode());
                raise.put("message", e.getMessage());
                if (e.getDescription() != null) raise.put("description", e.getDescription());
                n.getExtra().put("raise", raise);
            }
            case DslAction.AddWarningAction w -> {
                n.setOp("WARN");
                Map<String, Object> warn = new LinkedHashMap<>();
                warn.put("code", w.getCode());
                warn.put("message", w.getMessage());
                n.getExtra().put("warn", warn);
            }
            case DslAction.PushAtPathAction p -> {
                // PUSH: target path + a raw (JSON-friendly) value held in extra
                n.setOp("PUSH");
                n.setPath(segments(p.getPath()));
                n.getExtra().put("value", p.getValue());
                ctx.paths.add(p.getPath());
            }
            case DslAction.EnsureAction en -> {
                // ENSURE: target path + a value term node (literal / path / call)
                n.setOp("ENSURE");
                n.setPath(segments(en.getPath()));
                n.setRhs(compileRight(en.getValue(), ctx));
                ctx.paths.add(en.getPath());
            }
        }
        return n;
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static List<String> segments(String path) {
        if (path == null || path.isEmpty()) return List.of();
        return Arrays.asList(path.split("\\."));
    }

    private static String normaliseOp(String op) {
        if (op == null) return null;
        return switch (op.trim().toLowerCase()) {
            case "ismissing" -> "is missing";
            case "ispresent" -> "is present";
            default -> op.trim().toLowerCase();
        };
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((s == null ? "" : s).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (Exception e) {
            return "n/a";
        }
    }

    /** Mutable accumulator for referenced paths + functions. */
    private static final class Ctx {
        final Set<String> paths = new LinkedHashSet<>();
        final Set<String> functions = new LinkedHashSet<>();
    }
}
