package com.dslpipeline.testengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Golden-trace parity utility.
 *
 * A golden trace pins {@code input → fired rules → outputs → branch} so the same
 * IR produces identical behaviour across runtimes and across refactors. This
 * compares two traces and reports the precise field-level differences.
 *
 * @author Nikunj Malik
 */
public final class GoldenTrace {

    private GoldenTrace() {}

    /** The behaviour-defining keys that must match for two traces to be at parity. */
    private static final List<String> PARITY_KEYS =
            List.of("ruleId", "conditionsMet", "branch", "firedActions", "errors", "passed", "output");

    public static ParityResult compare(Map<String, Object> expected, Map<String, Object> actual) {
        ParityResult r = new ParityResult();
        if (expected == null || actual == null) {
            r.differences.add("one trace is null");
            return r;
        }
        for (String key : PARITY_KEYS) {
            Object e = expected.get(key);
            Object a = actual.get(key);
            if (!Objects.equals(e, a)) {
                r.differences.add(key + ": expected " + e + " but got " + a);
            }
        }
        return r;
    }

    public static class ParityResult {
        public List<String> differences = new ArrayList<>();
        public boolean atParity() { return differences.isEmpty(); }
    }
}
