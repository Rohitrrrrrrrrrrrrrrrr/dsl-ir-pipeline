package com.dslpipeline.pipeline;

import com.dslpipeline.extensions.ExtensionRegistry;
import com.dslpipeline.model.sl.StructuredLogic;
import com.dslpipeline.schema.DomainSchema;
import com.dslpipeline.term.Term;
import com.dslpipeline.term.TermParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 2 — SL linting: ambiguity removal, DAL alignment, completeness.
 *
 * Runs fast and deterministically BEFORE any LLM call. Errors block the
 * pipeline; warnings are informational. DAL/function checks only fire when a
 * schema / extension registry is available.
 *
 * @author Nikunj Malik
 */
@Component
public class SlLinter {

    private static final Pattern FRAGMENT = Pattern.compile(
            "([A-Za-z_][\\w.]*(?:\\([^()]*(?:\\([^()]*\\)[^()]*)*\\))?)\\s*" +
                    "(<=|>=|==|!=|<|>)\\s*" +
                    "(\"[^\"]*\"|'[^']*'|[\\w$.\\-]+)");

    private final ExtensionRegistry registry;

    public SlLinter(ExtensionRegistry registry) {
        this.registry = registry;
    }

    public LintResult lint(StructuredLogic sl, DomainSchema schema) {
        LintResult r = new LintResult();
        if (sl == null) {
            r.errors.add(issue("SL01", "Structured Logic is null."));
            return r;
        }
        if (sl.getClauses() == null || sl.getClauses().isEmpty()) {
            r.errors.add(issue("SL02", "No clauses parsed from the natural-language input."));
            return r;
        }

        // surface ambiguities recorded during NL→SL as warnings (do not block)
        if (sl.getAmbiguities() != null) {
            for (String a : sl.getAmbiguities()) {
                r.warnings.add(issue("SL-AMB", a));
            }
        }
        if (sl.getConfidence() < 0.5) {
            r.warnings.add(issue("SL-CONF", "Low normalisation confidence ("
                    + sl.getConfidence() + ") — author review strongly recommended."));
        }

        for (int i = 0; i < sl.getClauses().size(); i++) {
            StructuredLogic.Clause c = sl.getClauses().get(i);
            String idx = "[clause " + (i + 1) + "] ";

            if (c.getCondition() == null || c.getCondition().isBlank()) {
                r.errors.add(issue("SL10", idx + "condition is empty."));
                continue;
            }
            if (c.getCondition().toLowerCase().startsWith("(unparsed)")) {
                r.errors.add(issue("SL11", idx + "condition could not be parsed: " + c.getCondition()));
                continue;
            }
            lintConditionString(c.getCondition(), idx, schema, r);

            if ((c.getOutcome() == null || c.getOutcome().isBlank())
                    && (c.getElseOutcome() == null || c.getElseOutcome().isBlank())) {
                r.errors.add(issue("SL20", idx + "clause has neither a THEN nor an ELSE outcome."));
            }
            if (c.getSeverity() == null || c.getSeverity().isBlank()) {
                r.warnings.add(issue("SL21", idx + "severity not classified; defaulting to 'error'."));
            }
        }
        return r;
    }

    private void lintConditionString(String condition, String idx, DomainSchema schema, LintResult r) {
        Matcher m = FRAGMENT.matcher(condition);
        boolean any = false;
        while (m.find()) {
            any = true;
            String left = m.group(1);
            String op = m.group(2);
            lintTerm(left, op, idx, schema, r);
        }
        if (!any) {
            r.warnings.add(issue("SL12", idx + "no recognisable comparison in condition: \""
                    + condition + "\"."));
        }
    }

    private void lintTerm(String leftRaw, String op, String idx, DomainSchema schema, LintResult r) {
        Term term;
        try {
            term = TermParser.parse(leftRaw);
        } catch (RuntimeException e) {
            r.warnings.add(issue("SL13", idx + "left-hand term not parseable: " + e.getMessage()));
            return;
        }
        // collect functions + paths
        List<String> funcs = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        collect(term, funcs, paths);

        for (String fn : funcs) {
            if (!registry.has(fn)) {
                r.errors.add(issue("SL30", idx + "unknown function '" + fn
                        + "()' — not in the extension registry."));
            }
        }

        boolean ordered = op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
        String leftType = inferType(term);

        if (schema != null && !schema.isEmpty()) {
            for (String p : paths) {
                if (!schema.hasPath(p)) {
                    if (schema.knowsRootOf(p)) {
                        r.warnings.add(issue("SL40", idx + "field '" + p
                                + "' is not declared in the schema (DAL)."));
                    } else {
                        r.warnings.add(issue("SL41", idx + "entity in path '" + p
                                + "' is unknown to the schema (DAL)."));
                    }
                }
            }
            // type / operator mismatch
            if (ordered && term instanceof Term.PathTerm pt) {
                String t = schema.typeOf(pt.dotted());
                if (t != null && !DomainSchema.isOrderable(t)) {
                    r.errors.add(issue("SL42", idx + "ordered comparison '" + op + "' used on '"
                            + pt.dotted() + "' which is declared as '" + t + "'."));
                }
            }
        }
        if (ordered && "string".equals(leftType)) {
            r.warnings.add(issue("SL43", idx + "ordered comparison '" + op
                    + "' on a string-typed term may be unintended."));
        }
        if (ordered && "boolean".equals(leftType)) {
            r.errors.add(issue("SL44", idx + "ordered comparison '" + op
                    + "' is not valid on a boolean term."));
        }
    }

    private String inferType(Term term) {
        return switch (term) {
            case Term.LiteralTerm lit -> switch (lit.kind()) {
                case "num", "dec" -> "number";
                case "str" -> "string";
                case "bool" -> "boolean";
                case "date" -> "date";
                default -> "any";
            };
            case Term.CallTerm call -> {
                var fn = registry.resolve(call.name());
                yield fn != null ? fn.getReturnType() : "any";
            }
            case Term.PathTerm ignored -> "any";
        };
    }

    private void collect(Term term, List<String> funcs, List<String> paths) {
        switch (term) {
            case Term.PathTerm p -> paths.add(p.dotted());
            case Term.LiteralTerm ignored -> { }
            case Term.CallTerm c -> {
                funcs.add(c.name());
                for (Term a : c.args()) collect(a, funcs, paths);
            }
        }
    }

    private static Issue issue(String code, String message) {
        Issue i = new Issue();
        i.code = code;
        i.message = message;
        return i;
    }

    public static class LintResult {
        public List<Issue> errors = new ArrayList<>();
        public List<Issue> warnings = new ArrayList<>();
        public boolean ok() { return errors.isEmpty(); }
    }

    public static class Issue {
        public String code;
        public String message;
    }
}
