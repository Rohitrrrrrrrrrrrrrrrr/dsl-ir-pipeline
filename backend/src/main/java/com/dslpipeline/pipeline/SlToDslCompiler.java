package com.dslpipeline.pipeline;

import com.dslpipeline.model.dsl.ConditionNode;
import com.dslpipeline.model.dsl.DslAction;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.sl.StructuredLogic;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 3 — Structured Logic → DSL (formalisation layer).
 *
 * Converts each SL clause into the deterministic, diff-friendly {@link RuleDSL}
 * contract. The condition string is parsed into a {@link ConditionNode} tree by
 * {@link ConditionParser}; outcomes become THEN actions; else-outcomes become
 * ELSE actions.
 *
 * @author Nikunj Malik
 */
@Component
public class SlToDslCompiler {

    private static final Pattern ENSURE_SET = Pattern.compile(
            "(?:set|ensure)\\s+([A-Za-z_][\\w.]*)\\s*(?:=|to)\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    public RuleDSL compile(StructuredLogic sl) {
        RuleDSL dsl = new RuleDSL();
        dsl.setRuleId(sl.getRuleId() != null ? sl.getRuleId() : "rule_unnamed");
        dsl.setPriority(100);
        dsl.setHaltOnViolation(true);
        dsl.setRoundingMode("HALF_UP");
        dsl.getMetadata().put("title", sl.getTitle());
        dsl.getMetadata().put("originalNl", sl.getOriginalNl());
        dsl.getMetadata().put("slStrategy", sl.getStrategy());
        dsl.getMetadata().put("slConfidence", sl.getConfidence());

        List<StructuredLogic.Clause> clauses = sl.getClauses();
        if (clauses == null || clauses.isEmpty()) {
            throw new IllegalArgumentException("SL has no clauses to compile.");
        }

        if (clauses.size() == 1) {
            compileSingle(clauses.get(0), dsl);
        } else if (clauses.stream().allMatch(this::isRequirement)) {
            compileMergedRequirements(clauses, dsl);
        } else {
            // primary clause becomes the rule; remaining clauses recorded for traceability
            compileSingle(clauses.get(0), dsl);
            List<String> extra = new ArrayList<>();
            for (int i = 1; i < clauses.size(); i++) {
                extra.add("IF " + clauses.get(i).getCondition()
                        + " THEN " + clauses.get(i).getOutcome());
            }
            dsl.getMetadata().put("additionalClauses", extra);
            dsl.getMetadata().put("note",
                    "Multiple independent clauses detected — primary clause compiled; "
                            + "split the remainder into separate rules.");
        }
        return dsl;
    }

    private boolean isRequirement(StructuredLogic.Clause c) {
        return "REQUIREMENT_NOT_MET".equals(c.getElseCode());
    }

    private void compileSingle(StructuredLogic.Clause c, RuleDSL dsl) {
        dsl.setConditions(List.of(ConditionParser.parse(c.getCondition())));
        if (c.getOutcome() != null && !c.getOutcome().isBlank()
                && !"requirement satisfied".equalsIgnoreCase(c.getOutcome())) {
            DslAction then = buildAction(c.getSeverity(), c.getCode(), c.getMessage(), c.getOutcome());
            if (then != null) dsl.setActions(new ArrayList<>(List.of(then)));
        }
        if (c.getElseOutcome() != null && !c.getElseOutcome().isBlank()) {
            DslAction els = buildAction(c.getElseSeverity(), c.getElseCode(),
                    c.getElseMessage(), c.getElseOutcome());
            if (els != null) dsl.setElseActions(new ArrayList<>(List.of(els)));
        }
        if (dsl.getActions().isEmpty() && dsl.getElseActions().isEmpty()) {
            // a clause must yield at least one action
            dsl.setActions(new ArrayList<>(List.of(
                    new DslAction.AddErrorAction(
                            c.getCode() != null ? c.getCode() : "RULE_VIOLATION",
                            c.getMessage() != null ? c.getMessage() : c.getOutcome()))));
        }
    }

    private void compileMergedRequirements(List<StructuredLogic.Clause> clauses, RuleDSL dsl) {
        List<ConditionNode> conds = new ArrayList<>();
        List<String> reqs = new ArrayList<>();
        for (StructuredLogic.Clause c : clauses) {
            conds.add(ConditionParser.parse(c.getCondition()));
            reqs.add(c.getCondition());
        }
        ConditionNode merged = conds.size() == 1 ? conds.get(0)
                : new ConditionNode.GroupCondition("AND", conds);
        dsl.setConditions(List.of(merged));
        // when every requirement holds → eligible
        dsl.setActions(new ArrayList<>(List.of(
                new DslAction.EnsureAction("result.status", "ELIGIBLE"))));
        // when any requirement fails → error
        dsl.setElseActions(new ArrayList<>(List.of(
                new DslAction.AddErrorAction("ELIGIBILITY_NOT_MET",
                        "One or more eligibility requirements were not met: " + String.join("; ", reqs)))));
    }

    /** Build a DslAction from an outcome's severity / code / message / text. */
    private DslAction buildAction(String severity, String code, String message, String outcomeText) {
        String sev = severity == null ? "error" : severity.toLowerCase();
        String c = code == null ? "RULE_VIOLATION" : code;
        String msg = message != null ? message : outcomeText;

        return switch (sev) {
            case "warning" -> new DslAction.AddWarningAction(c, msg);
            case "ensure" -> buildEnsure(outcomeText, code);
            default -> new DslAction.AddErrorAction(c, msg);
        };
    }

    private DslAction buildEnsure(String outcomeText, String code) {
        if (outcomeText != null) {
            Matcher m = ENSURE_SET.matcher(outcomeText.trim());
            if (m.find()) {
                return new DslAction.EnsureAction(m.group(1), ConditionParser.parseScalar(m.group(2)));
            }
        }
        return new DslAction.EnsureAction("result.status", code != null ? code : "OK");
    }
}
