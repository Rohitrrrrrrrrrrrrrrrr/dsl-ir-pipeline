package com.dslpipeline.extensions;

import com.dslpipeline.numeric.DecimalMath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Example project-level extension pack ({@code acme}).
 *
 * Project packs add domain-specific, schema-aware behaviour. They are
 * namespaced (e.g. {@code acme.riskBand}) and may NOT shadow core namespaces.
 * This pack demonstrates the pattern QA can exercise.
 *
 * @author Nikunj Malik
 */
public final class ProjectExtensions {

    private ProjectExtensions() {}

    public static List<ExtensionFunction> acmePack() {
        List<ExtensionFunction> fns = new ArrayList<>();

        fns.add(new ExtensionFunction("acme.riskBand", "acme", List.of("number"), "string",
                true, false, "Maps a 0..1 risk score to a band: LOW / MEDIUM / HIGH.",
                a -> {
                    BigDecimal score = DecimalMath.toDecimal(a.get(0));
                    if (score == null) return "UNKNOWN";
                    if (score.compareTo(new BigDecimal("0.33")) < 0) return "LOW";
                    if (score.compareTo(new BigDecimal("0.66")) < 0) return "MEDIUM";
                    return "HIGH";
                }));

        fns.add(new ExtensionFunction("acme.isHighValue", "acme", List.of("decimal"), "boolean",
                true, false, "True when an amount is at or above the high-value threshold (10,000).",
                a -> {
                    BigDecimal amount = DecimalMath.toDecimal(a.get(0));
                    return amount != null && amount.compareTo(new BigDecimal("10000")) >= 0;
                }));

        return fns;
    }
}
