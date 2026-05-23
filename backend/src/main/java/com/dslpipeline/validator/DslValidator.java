package com.dslpipeline.validator;

import com.dslpipeline.extensions.ExtensionFunction;
import com.dslpipeline.extensions.ExtensionRegistry;
import com.dslpipeline.model.dsl.ConditionNode;
import com.dslpipeline.model.dsl.DslAction;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.term.Term;
import com.dslpipeline.term.TermParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stage 4 — DSL validation: syntax, schema, function checks.
 *
 * Enforces the DSL grammar deterministically: "If it cannot be parsed
 * deterministically → it is invalid DSL." Errors block; warnings are advisory.
 *
 * @author Nikunj Malik
 */
@Component
public class DslValidator {

    private static final Set<String> BINARY_OPS =
            Set.of(">", ">=", "<", "<=", "==", "!=", "in", "not in");
    private static final Set<String> UNARY_OPS =
            Set.of("is missing", "ismissing", "is present", "ispresent");

    private final ExtensionRegistry registry;

    public DslValidator(ExtensionRegistry registry) {
        this.registry = registry;
    }

    public Result validate(RuleDSL dsl) {
        Result r = new Result();
        if (dsl == null) {
            r.errors.add(issue("DSL01", "RuleDSL is null."));
            return r;
        }
        if (dsl.getConditions() == null || dsl.getConditions().isEmpty()) {
            r.errors.add(issue("DSL02", "conditions[] is empty."));
        } else {
            for (int i = 0; i < dsl.getConditions().size(); i++) {
                validateCondition(dsl.getConditions().get(i), "conditions[" + i + "]", r);
            }
        }
        boolean hasThen = dsl.getActions() != null && !dsl.getActions().isEmpty();
        boolean hasElse = dsl.getElseActions() != null && !dsl.getElseActions().isEmpty();
        if (!hasThen && !hasElse) {
            r.errors.add(issue("DSL03", "rule has neither actions[] (THEN) nor elseActions[] (ELSE)."));
        }
        if (dsl.getActions() != null) {
            for (int i = 0; i < dsl.getActions().size(); i++) {
                validateAction(dsl.getActions().get(i), "actions[" + i + "]", r);
            }
        }
        if (dsl.getElseActions() != null) {
            for (int i = 0; i < dsl.getElseActions().size(); i++) {
                validateAction(dsl.getElseActions().get(i), "elseActions[" + i + "]", r);
            }
        }
        if (dsl.getRoundingMode() != null
                && !dsl.getRoundingMode().equals("HALF_UP")
                && !dsl.getRoundingMode().equals("HALF_EVEN")) {
            r.errors.add(issue("DSL04", "roundingMode must be HALF_UP or HALF_EVEN, got: "
                    + dsl.getRoundingMode()));
        }
        return r;
    }

    private void validateCondition(ConditionNode node, String path, Result r) {
        if (node == null) {
            r.errors.add(issue("DSL10", path + " is null."));
            return;
        }
        switch (node) {
            case ConditionNode.LeafCondition leaf -> validateLeaf(leaf, path, r);
            case ConditionNode.GroupCondition g -> {
                if (g.getOperator() == null
                        || !(g.getOperator().equals("AND") || g.getOperator().equals("OR"))) {
                    r.errors.add(issue("DSL11", path + ".operator must be AND or OR."));
                }
                if (g.getConditions() == null) {
                    r.errors.add(issue("DSL12", path + ".conditions[] is missing."));
                } else {
                    for (int i = 0; i < g.getConditions().size(); i++) {
                        validateCondition(g.getConditions().get(i),
                                path + ".conditions[" + i + "]", r);
                    }
                }
            }
            case ConditionNode.NotCondition n -> {
                if (n.getCondition() == null) r.errors.add(issue("DSL13", path + ".condition required."));
                else validateCondition(n.getCondition(), path + ".condition", r);
            }
            case ConditionNode.MinusCondition m -> {
                if (m.getInclude() == null) r.errors.add(issue("DSL14", path + ".include required."));
                else validateCondition(m.getInclude(), path + ".include", r);
                if (m.getExclude() == null || m.getExclude().isEmpty()) {
                    r.errors.add(issue("DSL15", path + ".exclude[] is required."));
                } else {
                    for (int i = 0; i < m.getExclude().size(); i++) {
                        validateCondition(m.getExclude().get(i), path + ".exclude[" + i + "]", r);
                    }
                }
            }
            case ConditionNode.ExistsCondition e ->
                    validateQuantifier(e.getCollection(), e.getAs(), e.getCondition(),
                            path, "exists", r);
            case ConditionNode.ForAllCondition f ->
                    validateQuantifier(f.getCollection(), f.getAs(), f.getCondition(),
                            path, "forAll", r);
            case ConditionNode.RuleRefCondition ref -> {
                if (ref.getRuleId() == null || ref.getRuleId().isBlank()) {
                    r.errors.add(issue("DSL16", path + ".ruleId required."));
                }
            }
            case ConditionNode.CountWhereCondition cw -> {
                if (cw.getCollection() == null || cw.getCollection().isBlank()) {
                    r.errors.add(issue("DSL17", path + ".collection required."));
                }
                if (cw.getCondition() == null) {
                    r.errors.add(issue("DSL18", path + ".condition required."));
                } else {
                    validateCondition(cw.getCondition(), path + ".condition", r);
                }
                if (cw.getOp() == null
                        || !Set.of(">", ">=", "<", "<=", "==", "!=").contains(cw.getOp())) {
                    r.errors.add(issue("DSL19", path + ".op must be a comparison operator."));
                }
                if (cw.getValue() == null) {
                    r.errors.add(issue("DSL20", path + ".value (numeric threshold) required."));
                }
            }
            case ConditionNode.DecisionTableCondition dt -> {
                if (dt.getInputs() == null || dt.getInputs().isEmpty()) {
                    r.errors.add(issue("DSL21", path + ".inputs[] required."));
                }
                if (dt.getRows() == null) {
                    r.errors.add(issue("DSL22", path + ".rows[] required."));
                } else {
                    int expected = dt.getInputs() == null ? 0 : dt.getInputs().size();
                    for (int i = 0; i < dt.getRows().size(); i++) {
                        var row = dt.getRows().get(i);
                        int got = row.getWhen() == null ? 0 : row.getWhen().size();
                        if (got != expected) {
                            r.errors.add(issue("DSL23", path + ".rows[" + i + "].when arity "
                                    + got + " != inputs arity " + expected + "."));
                        }
                        if (row.getThen() == null) {
                            r.errors.add(issue("DSL24", path + ".rows[" + i + "].then required."));
                        }
                    }
                }
            }
        }
    }

    private void validateLeaf(ConditionNode.LeafCondition leaf, String path, Result r) {
        if (leaf.getLeft() == null || leaf.getLeft().isBlank()) {
            r.errors.add(issue("DSL30", path + ".left is required."));
        } else {
            checkFunctions(leaf.getLeft(), path + ".left", r);
        }
        String op = leaf.getOp() == null ? "" : leaf.getOp().toLowerCase().trim();
        boolean unary = UNARY_OPS.contains(op);
        boolean binary = BINARY_OPS.contains(op);
        if (!unary && !binary) {
            r.errors.add(issue("DSL31", path + ".op invalid: '" + leaf.getOp() + "'."));
        }
        if (binary && leaf.getRight() == null) {
            r.errors.add(issue("DSL32", path + ".right required for binary op '" + leaf.getOp() + "'."));
        }
        if ((op.equals("in") || op.equals("not in")) && !(leaf.getRight() instanceof List<?>)) {
            r.errors.add(issue("DSL33", path + ".right must be a JSON array for '" + leaf.getOp() + "'."));
        }
        if (binary && !op.equals("in") && !op.equals("not in")
                && leaf.getRight() instanceof String s) {
            checkFunctions(s, path + ".right", r);
        }
    }

    /** Parse a term string and verify every function call is in the registry, with correct arity. */
    private void checkFunctions(String text, String where, Result r) {
        Term term;
        try {
            term = TermParser.parse(text);
        } catch (RuntimeException e) {
            r.warnings.add(issue("DSL34", where + " could not be parsed as a term: " + e.getMessage()));
            return;
        }
        checkFunctionsInTerm(term, where, r);
    }

    private void checkFunctionsInTerm(Term term, String where, Result r) {
        switch (term) {
            case Term.PathTerm ignored -> { }
            case Term.LiteralTerm ignored -> { }
            case Term.CallTerm c -> {
                ExtensionFunction fn = registry.resolve(c.name());
                if (fn == null) {
                    r.errors.add(issue("DSL35", where + " calls unknown function '" + c.name() + "()'."));
                } else {
                    int got = c.args().size();
                    if (fn.isVariadic()) {
                        if (got < fn.arity() - 1) {
                            r.errors.add(issue("DSL36", where + " " + fn.signature()
                                    + " needs at least " + (fn.arity() - 1) + " args, got " + got + "."));
                        }
                    } else if (got != fn.arity()) {
                        r.errors.add(issue("DSL36", where + " " + fn.signature()
                                + " needs " + fn.arity() + " args, got " + got + "."));
                    }
                }
                for (Term a : c.args()) checkFunctionsInTerm(a, where, r);
            }
        }
    }

    private void validateQuantifier(String collection, String as, ConditionNode condition,
                                    String path, String name, Result r) {
        if (collection == null || collection.isBlank()) {
            r.errors.add(issue("DSL40", path + " (" + name + ") .collection is required."));
        }
        if (as == null || as.isBlank()) {
            r.warnings.add(issue("DSL41", path + " (" + name + ") .as alias is recommended."));
        }
        if (condition == null) {
            r.errors.add(issue("DSL42", path + " (" + name + ") .condition is required."));
        } else {
            validateCondition(condition, path + ".condition", r);
        }
    }

    private void validateAction(DslAction act, String path, Result r) {
        if (act == null) {
            r.errors.add(issue("DSL50", path + " is null."));
            return;
        }
        switch (act) {
            case DslAction.AddErrorAction e -> {
                if (e.getCode() == null || e.getCode().isBlank()) {
                    r.errors.add(issue("DSL51", path + ".code required for addError."));
                }
            }
            case DslAction.AddWarningAction w -> {
                if (w.getCode() == null || w.getCode().isBlank()) {
                    r.errors.add(issue("DSL52", path + ".code required for addWarning."));
                }
            }
            case DslAction.PushAtPathAction p -> {
                if (p.getPath() == null || p.getPath().isBlank()) {
                    r.errors.add(issue("DSL53", path + ".path required for collection.pushAtPath."));
                }
                if (p.getValue() == null) {
                    r.errors.add(issue("DSL54", path + ".value required for collection.pushAtPath."));
                }
            }
            case DslAction.EnsureAction en -> {
                if (en.getPath() == null || en.getPath().isBlank()) {
                    r.errors.add(issue("DSL55", path + ".path required for ensure."));
                }
            }
        }
    }

    private static Issue issue(String code, String msg) {
        Issue i = new Issue();
        i.code = code;
        i.message = msg;
        return i;
    }

    public static class Result {
        public List<Issue> errors = new ArrayList<>();
        public List<Issue> warnings = new ArrayList<>();
        public boolean ok() { return errors.isEmpty(); }
    }

    public static class Issue {
        public String code;
        public String message;
    }
}
