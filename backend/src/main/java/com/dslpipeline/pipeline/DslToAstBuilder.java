package com.dslpipeline.pipeline;

import com.dslpipeline.model.ast.AstNode;
import com.dslpipeline.model.dsl.ConditionNode;
import com.dslpipeline.model.dsl.DslAction;
import com.dslpipeline.model.dsl.RuleDSL;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage 5 — DSL → AST (semantic parsing layer).
 *
 * Produces the parsed structural form of the rule: a RULE root with WHEN / THEN
 * / ELSE branches and explicit "kind" tags on every node so the type checker,
 * optimizer and IR builder can walk the tree generically.
 *
 * "AST is the parsed structure of the DSL (syntactic form)."
 *
 * @author Nikunj Malik
 */
@Component
public class DslToAstBuilder {

    public AstNode build(RuleDSL dsl) {
        AstNode root = new AstNode("RULE")
                .attr("ruleId", dsl.getRuleId())
                .attr("priority", dsl.getPriority())
                .attr("haltOnViolation", dsl.isHaltOnViolation())
                .attr("roundingMode", dsl.getRoundingMode())
                .attr("effectiveFrom", dsl.getEffectiveFrom())
                .attr("effectiveTo", dsl.getEffectiveTo());

        AstNode when = new AstNode("WHEN");
        for (ConditionNode c : dsl.getConditions()) {
            when.child(buildCondition(c));
        }
        root.child(when);

        AstNode then = new AstNode("THEN");
        if (dsl.getActions() != null) {
            for (DslAction a : dsl.getActions()) then.child(buildAction(a));
        }
        root.child(then);

        AstNode els = new AstNode("ELSE");
        if (dsl.getElseActions() != null) {
            for (DslAction a : dsl.getElseActions()) els.child(buildAction(a));
        }
        root.child(els);

        return root;
    }

    private AstNode buildCondition(ConditionNode node) {
        return switch (node) {
            case ConditionNode.LeafCondition l -> new AstNode("CONDITION:leaf")
                    .attr("left", l.getLeft())
                    .attr("op", normaliseOp(l.getOp()))
                    .attr("right", l.getRight());
            case ConditionNode.GroupCondition g -> {
                AstNode n = new AstNode("CONDITION:group").attr("operator", g.getOperator());
                if (g.getConditions() != null) {
                    for (ConditionNode c : g.getConditions()) n.child(buildCondition(c));
                }
                yield n;
            }
            case ConditionNode.NotCondition not -> {
                AstNode n = new AstNode("CONDITION:not");
                if (not.getCondition() != null) n.child(buildCondition(not.getCondition()));
                yield n;
            }
            case ConditionNode.MinusCondition m -> {
                AstNode n = new AstNode("CONDITION:minus");
                if (m.getInclude() != null) {
                    n.child(new AstNode("INCLUDE").child(buildCondition(m.getInclude())));
                }
                AstNode ex = new AstNode("EXCLUDE");
                if (m.getExclude() != null) {
                    for (ConditionNode c : m.getExclude()) ex.child(buildCondition(c));
                }
                n.child(ex);
                yield n;
            }
            case ConditionNode.ExistsCondition e -> {
                AstNode n = new AstNode("CONDITION:exists")
                        .attr("collection", e.getCollection())
                        .attr("as", e.getAs());
                if (e.getCondition() != null) n.child(buildCondition(e.getCondition()));
                yield n;
            }
            case ConditionNode.ForAllCondition f -> {
                AstNode n = new AstNode("CONDITION:forAll")
                        .attr("collection", f.getCollection())
                        .attr("as", f.getAs());
                if (f.getCondition() != null) n.child(buildCondition(f.getCondition()));
                yield n;
            }
            case ConditionNode.RuleRefCondition r -> new AstNode("CONDITION:ruleRef")
                    .attr("ruleId", r.getRuleId());
            case ConditionNode.CountWhereCondition c -> {
                AstNode n = new AstNode("CONDITION:countWhere")
                        .attr("collection", c.getCollection())
                        .attr("as", c.getAs())
                        .attr("op", c.getOp())
                        .attr("value", c.getValue());
                if (c.getCondition() != null) n.child(buildCondition(c.getCondition()));
                yield n;
            }
            case ConditionNode.DecisionTableCondition dt -> new AstNode("CONDITION:decisionTable")
                    .attr("inputs", dt.getInputs())
                    .attr("rows", dt.getRows())
                    .attr("otherwise", dt.getOtherwise());
        };
    }

    private AstNode buildAction(DslAction a) {
        return switch (a) {
            case DslAction.AddErrorAction e -> new AstNode("ACTION:addError")
                    .attr("code", e.getCode()).attr("message", e.getMessage());
            case DslAction.AddWarningAction w -> new AstNode("ACTION:addWarning")
                    .attr("code", w.getCode()).attr("message", w.getMessage());
            case DslAction.PushAtPathAction p -> new AstNode("ACTION:pushAtPath")
                    .attr("path", p.getPath()).attr("value", p.getValue());
            case DslAction.EnsureAction en -> new AstNode("ACTION:ensure")
                    .attr("path", en.getPath()).attr("value", en.getValue());
        };
    }

    private static String normaliseOp(String op) {
        if (op == null) return null;
        return switch (op.trim().toLowerCase()) {
            case "ismissing" -> "is missing";
            case "ispresent" -> "is present";
            default -> op.trim().toLowerCase();
        };
    }
}
