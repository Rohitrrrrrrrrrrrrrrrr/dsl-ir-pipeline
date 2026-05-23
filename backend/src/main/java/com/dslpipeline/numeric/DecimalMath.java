package com.dslpipeline.numeric;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Deterministic decimal arithmetic.
 *
 * All numeric values in the rule pipeline pass through this class so that
 * rounding, scale and division behave identically on every runtime. Binary
 * floating point ({@code double}/{@code float}) is never used for rule maths.
 *
 * @author Nikunj Malik
 */
public final class DecimalMath {

    private DecimalMath() {}

    public static RoundingMode mode(NumericProfile p) {
        return mode(p == null ? "HALF_UP" : p.getRounding());
    }

    public static RoundingMode mode(String rounding) {
        return "HALF_EVEN".equals(NumericProfile.normaliseRounding(rounding))
                ? RoundingMode.HALF_EVEN : RoundingMode.HALF_UP;
    }

    /** Coerce any supported value into a BigDecimal, or null if not numeric. */
    public static BigDecimal toDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Integer i) return BigDecimal.valueOf(i);
        if (o instanceof Long l) return BigDecimal.valueOf(l);
        if (o instanceof Short s) return BigDecimal.valueOf(s.longValue());
        if (o instanceof Byte b) return BigDecimal.valueOf(b.longValue());
        if (o instanceof Double d) return new BigDecimal(Double.toString(d));
        if (o instanceof Float f) return new BigDecimal(Float.toString(f));
        if (o instanceof Boolean) return null;
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parse a {@code dec("...")} literal payload string into a scaled BigDecimal. */
    public static BigDecimal parseDecLiteral(String raw) {
        if (raw == null) throw new IllegalArgumentException("dec() literal is null");
        String s = raw.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }
        return new BigDecimal(s);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b, NumericProfile p) {
        return a.add(b, ctx(p));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b, NumericProfile p) {
        return a.subtract(b, ctx(p));
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b, NumericProfile p) {
        return a.multiply(b, ctx(p));
    }

    /** Division with explicit scale + rounding (no non-terminating-expansion errors). */
    public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale, NumericProfile p) {
        if (b == null || b.signum() == 0) {
            throw new ArithmeticException("division by zero");
        }
        return a.divide(b, scale, mode(p));
    }

    /** Quantise to a fixed scale using the profile rounding mode. */
    public static BigDecimal quantize(BigDecimal value, int scale, NumericProfile p) {
        if (value == null) return null;
        return value.setScale(scale, mode(p));
    }

    /** Compare two arbitrary numeric values, returns -1/0/1, or null if not comparable. */
    public static Integer compare(Object a, Object b) {
        BigDecimal da = toDecimal(a);
        BigDecimal db = toDecimal(b);
        if (da == null || db == null) return null;
        return Integer.signum(da.compareTo(db));
    }

    private static MathContext ctx(NumericProfile p) {
        int precision = p == null ? 34 : p.getPrecision();
        return new MathContext(precision, mode(p));
    }
}
