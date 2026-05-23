package com.dslpipeline.pipeline;

import com.dslpipeline.extensions.ExtensionFunction;
import com.dslpipeline.extensions.ExtensionRegistry;
import com.dslpipeline.model.ast.AstNode;
import com.dslpipeline.schema.DomainSchema;
import com.dslpipeline.term.Term;
import com.dslpipeline.term.TermParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stage 6 — Type checking + schema resolution.
 *
 * Walks the AST, parses every condition term, resolves its type against the
 * domain schema and extension registry, and enforces:
 *   - extension functions exist, with correct arity and argument types
 *   - ordered comparisons operate on orderable types
 *   - membership operators ({@code in}/{@code not in}) have a list on the right
 *
 * "AST is where correctness is enforced."
 *
 * @author Nikunj Malik
 */
@Component
public class TypeChecker {

    private static final Set<String> ORDERED = Set.of("<", "<=", ">", ">=");

    private final ExtensionRegistry registry;

    public TypeChecker(ExtensionRegistry registry) {
        this.registry = registry;
    }

    public Result check(AstNode root, DomainSchema schema) {
        Result r = new Result();
        if (root == null) {
            r.errors.add("AST root is null.");
            return r;
        }
        walk(root, schema, r);
        return r;
    }

    private void walk(AstNode n, DomainSchema schema, Result r) {
        if (n == null) return;
        if ("CONDITION:leaf".equals(n.getKind())) {
            checkLeaf(n, schema, r);
        }
        for (AstNode c : n.getChildren()) walk(c, schema, r);
    }

    private void checkLeaf(AstNode leaf, DomainSchema schema, Result r) {
        Object leftObj = leaf.getAttributes().get("left");
        Object opObj = leaf.getAttributes().get("op");
        Object right = leaf.getAttributes().get("right");
        if (leftObj == null || opObj == null) {
            r.errors.add("Leaf condition missing left/op.");
            return;
        }
        String left = leftObj.toString();
        String op = opObj.toString().toLowerCase();

        Term leftTerm;
        try {
            leftTerm = TermParser.parse(left);
        } catch (RuntimeException e) {
            r.errors.add("Left term '" + left + "' is not parseable: " + e.getMessage());
            return;
        }

        // recursively type-check function calls inside the left term
        String leftType = checkTerm(leftTerm, schema, r);

        boolean unary = op.equals("is missing") || op.equals("ismissing")
                || op.equals("is present") || op.equals("ispresent");

        if (ORDERED.contains(op)) {
            if (leftType != null && !leftType.equals("any")
                    && !DomainSchema.isOrderable(leftType)) {
                r.errors.add("Type error: ordered op '" + op + "' requires a numeric/date term, "
                        + "but '" + left + "' resolves to '" + leftType + "'.");
            }
            String rightType = literalType(right);
            if (rightType != null && !rightType.equals("any")
                    && !DomainSchema.isOrderable(rightType)) {
                r.errors.add("Type error: right-hand side of '" + op + "' must be numeric/date, "
                        + "got '" + right + "' (" + rightType + ").");
            }
        }

        if ((op.equals("in") || op.equals("not in")) && !(right instanceof List<?>)) {
            r.errors.add("Type error: operator '" + op + "' requires a list on the right-hand side.");
        }

        if (!unary && !op.equals("in") && !op.equals("not in") && right == null) {
            r.errors.add("Type error: operator '" + op + "' requires a right-hand value.");
        }
    }

    /** Type-check a term; returns its resolved type ("number"/"date"/"string"/"boolean"/"any"). */
    private String checkTerm(Term term, DomainSchema schema, Result r) {
        return switch (term) {
            case Term.LiteralTerm lit -> switch (lit.kind()) {
                case "num", "dec" -> "number";
                case "str" -> "string";
                case "bool" -> "boolean";
                case "date" -> "date";
                default -> "any";
            };
            case Term.PathTerm path -> {
                String dotted = path.dotted();
                if (schema != null && !schema.isEmpty()) {
                    String t = schema.typeOf(dotted);
                    if (t == null) {
                        if (schema.knowsRootOf(dotted)) {
                            r.warnings.add("Path '" + dotted + "' not declared in schema (DAL).");
                        } else {
                            r.warnings.add("Entity in path '" + dotted + "' unknown to schema (DAL).");
                        }
                        yield "any";
                    }
                    yield t;
                }
                yield "any";
            }
            case Term.CallTerm call -> {
                ExtensionFunction fn = registry.resolve(call.name());
                if (fn == null) {
                    r.errors.add("Unknown extension function: " + call.name() + "().");
                    yield "any";
                }
                // arity
                int got = call.args().size();
                int need = fn.arity();
                if (fn.isVariadic()) {
                    if (got < need - 1) {
                        r.errors.add("Function " + fn.signature() + " expects at least "
                                + (need - 1) + " args, got " + got + ".");
                    }
                } else if (got != need) {
                    r.errors.add("Function " + fn.signature() + " expects " + need
                            + " args, got " + got + ".");
                }
                // arg types
                for (int i = 0; i < call.args().size(); i++) {
                    String argType = checkTerm(call.args().get(i), schema, r);
                    String want = i < fn.getArgTypes().size()
                            ? fn.getArgTypes().get(i)
                            : fn.getArgTypes().get(fn.getArgTypes().size() - 1);
                    if (!compatible(want, argType)) {
                        r.warnings.add("Function " + call.name() + " arg " + (i + 1)
                                + " expects '" + want + "' but got '" + argType + "'.");
                    }
                }
                yield fn.getReturnType();
            }
        };
    }

    private String literalType(Object right) {
        if (right == null) return null;
        if (right instanceof Number) return "number";
        if (right instanceof Boolean) return "boolean";
        if (right instanceof List<?>) return "list";
        String s = right.toString();
        if (s.matches("[-+]?\\d+(?:\\.\\d+)?")) return "number";
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) return "date";
        return "string";
    }

    private boolean compatible(String want, String got) {
        if (want == null || got == null) return true;
        if (want.equals("any") || got.equals("any")) return true;
        if (want.equals(got)) return true;
        boolean wantNum = DomainSchema.isNumeric(want) || want.equals("number");
        boolean gotNum = DomainSchema.isNumeric(got) || got.equals("number");
        if (wantNum && gotNum) return true;
        // dates are commonly carried as strings
        if (want.equals("date") && (got.equals("string") || got.equals("date"))) return true;
        if (want.equals("collection") && got.equals("collection")) return true;
        return false;
    }

    public static class Result {
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public boolean ok() { return errors.isEmpty(); }
    }
}
