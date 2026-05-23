package com.dslpipeline.numeric;

/**
 * Deterministic numeric profile carried into the IR.
 *
 * Per the platform principle "Validate Once, Execute Anywhere", every IR
 * declares an explicit decimal profile so that TS / Java / .NET / Go / Rust
 * runtimes produce byte-identical results. No binary float arithmetic is ever
 * used in the rule pipeline.
 *
 * @author Nikunj Malik
 */
public class NumericProfile {

    /** Maximum significant digits (MathContext precision). */
    private int precision = 34;

    /** Default scale (decimal places) for amounts when none is given. */
    private int defaultScale = 2;

    /** Scale for rates / factors. */
    private int rateScale = 12;

    /** Rounding mode: HALF_UP | HALF_EVEN. */
    private String rounding = "HALF_UP";

    public NumericProfile() {}

    public NumericProfile(String rounding) {
        this.rounding = normaliseRounding(rounding);
    }

    public static String normaliseRounding(String mode) {
        if (mode == null) return "HALF_UP";
        String m = mode.trim().toUpperCase().replace('-', '_');
        return switch (m) {
            case "HALF_EVEN", "BANKERS", "BANKER" -> "HALF_EVEN";
            default -> "HALF_UP";
        };
    }

    public int getPrecision() { return precision; }
    public void setPrecision(int precision) { this.precision = precision; }

    public int getDefaultScale() { return defaultScale; }
    public void setDefaultScale(int defaultScale) { this.defaultScale = defaultScale; }

    public int getRateScale() { return rateScale; }
    public void setRateScale(int rateScale) { this.rateScale = rateScale; }

    public String getRounding() { return rounding; }
    public void setRounding(String rounding) { this.rounding = normaliseRounding(rounding); }
}
