package com.dslpipeline.term;

import com.dslpipeline.extensions.ExtensionFunction;
import com.dslpipeline.extensions.ExtensionRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a parsed {@link Term} against a payload using the extension registry.
 *
 * Two modes:
 *   - full evaluation     — payload supplied; paths are resolved.
 *   - constant evaluation — payload null; a {@link NotConstantException} is thrown
 *                           if a path is encountered (used by the AST optimizer's
 *                           constant-folding pass).
 *
 * Every function call may be recorded into a trace list for auditability.
 *
 * @author Nikunj Malik
 */
@Component
public class TermEvaluator {

    private final ExtensionRegistry registry;

    public TermEvaluator(ExtensionRegistry registry) {
        this.registry = registry;
    }

    /** Full evaluation against a payload. trace may be null. */
    public Object evaluate(Term term, Map<String, Object> payload, List<String> trace) {
        return eval(term, payload, false, trace);
    }

    /** Constant-only evaluation — throws {@link NotConstantException} on any path. */
    public Object evaluateConstant(Term term) {
        return eval(term, null, true, null);
    }

    /** True when a term contains no path references (i.e. is a pure constant expression). */
    public boolean isConstant(Term term) {
        return switch (term) {
            case Term.PathTerm ignored -> false;
            case Term.LiteralTerm ignored -> true;
            case Term.CallTerm c -> c.args().stream().allMatch(this::isConstant);
        };
    }

    private Object eval(Term term, Map<String, Object> payload, boolean constantOnly, List<String> trace) {
        return switch (term) {
            case Term.LiteralTerm lit -> lit.value();
            case Term.PathTerm path -> {
                if (constantOnly) {
                    throw new NotConstantException("path '" + path.dotted() + "' is not a constant");
                }
                yield readPath(payload, path.segments());
            }
            case Term.CallTerm call -> {
                ExtensionFunction fn = registry.resolve(call.name());
                if (fn == null) {
                    throw new EvaluationException("unknown function: " + call.name() + "()");
                }
                List<Object> args = new ArrayList<>(call.args().size());
                for (Term arg : call.args()) {
                    args.add(eval(arg, payload, constantOnly, trace));
                }
                Object result = fn.invoke(args);
                if (trace != null) {
                    trace.add("fn " + call.name() + "(" + args + ") => " + result);
                }
                yield result;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Object readPath(Map<String, Object> payload, List<String> segments) {
        if (payload == null || segments == null || segments.isEmpty()) return null;
        Object cur = payload;
        for (String seg : segments) {
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = ((Map<String, Object>) m).get(seg);
            if (cur == null) return null;
        }
        return cur;
    }

    /** Thrown when a constant-only evaluation encounters a path. */
    public static final class NotConstantException extends RuntimeException {
        public NotConstantException(String message) { super(message); }
    }

    /** Thrown when a term cannot be evaluated (unknown function, bad args). */
    public static final class EvaluationException extends RuntimeException {
        public EvaluationException(String message) { super(message); }
    }
}
