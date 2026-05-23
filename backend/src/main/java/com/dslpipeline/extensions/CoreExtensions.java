package com.dslpipeline.extensions;

import com.dslpipeline.numeric.DecimalMath;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Core extension packs — platform-level, deterministic, universally available.
 *
 * Packs: {@code date}, {@code collection}, {@code string}, {@code logic}.
 * All functions are pure, UTC-safe (date-only), and free of ambient state
 * (no system clock — a reference date must always be supplied explicitly).
 *
 * @author Nikunj Malik
 */
public final class CoreExtensions {

    private CoreExtensions() {}

    /** Build every core function (registered under namespaced + bare names by the registry). */
    public static List<ExtensionFunction> all() {
        List<ExtensionFunction> fns = new ArrayList<>();
        date(fns);
        collection(fns);
        string(fns);
        logic(fns);
        return fns;
    }

    // ─────────────────────────── date pack ───────────────────────────

    private static void date(List<ExtensionFunction> fns) {
        fns.add(fn("date.calculateAge", "date", List.of("date", "date"), "number",
                "Full years between birthDate and referenceDate (month/day aware).",
                a -> (long) Period.between(reqDate(a, 0), reqDate(a, 1)).getYears()));

        fns.add(fn("date.compareDates", "date", List.of("date", "date"), "number",
                "Returns -1, 0 or 1 comparing date a to date b.",
                a -> (long) Integer.signum(reqDate(a, 0).compareTo(reqDate(a, 1)))));

        fns.add(fn("date.diffDays", "date", List.of("date", "date"), "number",
                "Signed whole-day difference a - b (UTC).",
                a -> ChronoUnit.DAYS.between(reqDate(a, 1), reqDate(a, 0))));

        fns.add(fn("date.daysBetween", "date", List.of("date", "date"), "number",
                "Absolute whole-day difference between two dates (UTC).",
                a -> Math.abs(ChronoUnit.DAYS.between(reqDate(a, 1), reqDate(a, 0)))));

        fns.add(fn("date.addDays", "date", List.of("date", "number"), "date",
                "Adds N calendar days, returns an ISO date string.",
                a -> reqDate(a, 0).plusDays(reqLong(a, 1)).toString()));

        fns.add(fn("date.addMonths", "date", List.of("date", "number"), "date",
                "Adds N calendar months, returns an ISO date string.",
                a -> reqDate(a, 0).plusMonths(reqLong(a, 1)).toString()));

        fns.add(fn("date.isBefore", "date", List.of("date", "date"), "boolean",
                "True when date a is strictly before date b.",
                a -> reqDate(a, 0).isBefore(reqDate(a, 1))));

        fns.add(fn("date.isAfter", "date", List.of("date", "date"), "boolean",
                "True when date a is strictly after date b.",
                a -> reqDate(a, 0).isAfter(reqDate(a, 1))));

        fns.add(fn("date.isOnOrAfter", "date", List.of("date", "date"), "boolean",
                "True when date a is on or after date b.",
                a -> !reqDate(a, 0).isBefore(reqDate(a, 1))));

        fns.add(fn("date.isBetween", "date", List.of("date", "date", "date", "boolean"), "boolean",
                "True when x lies within [start, end] (inclusive flag controls the bounds).",
                a -> {
                    LocalDate x = reqDate(a, 0), s = reqDate(a, 1), e = reqDate(a, 2);
                    boolean inclusive = a.size() < 4 || Boolean.TRUE.equals(a.get(3));
                    if (inclusive) return !x.isBefore(s) && !x.isAfter(e);
                    return x.isAfter(s) && x.isBefore(e);
                }));

        fns.add(fn("date.isWeekend", "date", List.of("date"), "boolean",
                "True when the date falls on Saturday or Sunday.",
                a -> {
                    DayOfWeek d = reqDate(a, 0).getDayOfWeek();
                    return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
                }));

        fns.add(fn("date.startOfMonth", "date", List.of("date"), "date",
                "First day of the month for the given date.",
                a -> reqDate(a, 0).withDayOfMonth(1).toString()));

        fns.add(fn("date.endOfMonth", "date", List.of("date"), "date",
                "Last day of the month for the given date.",
                a -> { LocalDate d = reqDate(a, 0); return d.withDayOfMonth(d.lengthOfMonth()).toString(); }));

        fns.add(fn("date.withinDays", "date", List.of("date", "date", "number"), "boolean",
                "True when |a - b| is within N days.",
                a -> Math.abs(ChronoUnit.DAYS.between(reqDate(a, 1), reqDate(a, 0))) <= reqLong(a, 2)));
    }

    // ─────────────────────────── collection pack ───────────────────────────

    private static void collection(List<ExtensionFunction> fns) {
        fns.add(fn("collection.count", "collection", List.of("collection"), "number",
                "Number of elements in the collection.",
                a -> (long) reqList(a, 0).size()));

        fns.add(fn("collection.sum", "collection", List.of("collection"), "decimal",
                "Decimal-safe sum of numeric elements.",
                a -> {
                    BigDecimal acc = BigDecimal.ZERO;
                    for (Object o : reqList(a, 0)) {
                        BigDecimal d = DecimalMath.toDecimal(o);
                        if (d != null) acc = acc.add(d);
                    }
                    return acc;
                }));

        fns.add(fn("collection.avg", "collection", List.of("collection"), "decimal",
                "Decimal-safe mean of numeric elements (HALF_UP, scale 8).",
                a -> {
                    List<Object> xs = reqList(a, 0);
                    BigDecimal acc = BigDecimal.ZERO;
                    int n = 0;
                    for (Object o : xs) {
                        BigDecimal d = DecimalMath.toDecimal(o);
                        if (d != null) { acc = acc.add(d); n++; }
                    }
                    if (n == 0) return BigDecimal.ZERO;
                    return acc.divide(BigDecimal.valueOf(n), 8, RoundingMode.HALF_UP);
                }));

        fns.add(fn("collection.min", "collection", List.of("collection"), "decimal",
                "Minimum numeric element.",
                a -> reduceNumeric(reqList(a, 0), true)));

        fns.add(fn("collection.max", "collection", List.of("collection"), "decimal",
                "Maximum numeric element.",
                a -> reduceNumeric(reqList(a, 0), false)));

        fns.add(fn("collection.isEmpty", "collection", List.of("collection"), "boolean",
                "True when the collection has no elements.",
                a -> reqList(a, 0).isEmpty()));

        fns.add(fn("collection.distinctCount", "collection", List.of("collection"), "number",
                "Number of distinct elements.",
                a -> (long) new java.util.HashSet<>(reqList(a, 0)).size()));
    }

    // ─────────────────────────── string pack ───────────────────────────

    private static void string(List<ExtensionFunction> fns) {
        fns.add(fn("string.length", "string", List.of("string"), "number",
                "Character length of the string.",
                a -> (long) reqStr(a, 0).length()));

        fns.add(fn("string.upper", "string", List.of("string"), "string",
                "Upper-cased string.",
                a -> reqStr(a, 0).toUpperCase()));

        fns.add(fn("string.lower", "string", List.of("string"), "string",
                "Lower-cased string.",
                a -> reqStr(a, 0).toLowerCase()));

        fns.add(fn("string.trim", "string", List.of("string"), "string",
                "Whitespace-trimmed string.",
                a -> reqStr(a, 0).trim()));

        fns.add(fn("string.contains", "string", List.of("string", "string"), "boolean",
                "True when the first string contains the second.",
                a -> reqStr(a, 0).contains(reqStr(a, 1))));

        fns.add(fn("string.startsWith", "string", List.of("string", "string"), "boolean",
                "True when the first string starts with the second.",
                a -> reqStr(a, 0).startsWith(reqStr(a, 1))));

        fns.add(fn("string.concat", "string", List.of("string", "string"), "string",
                "Concatenation of two strings.",
                a -> reqStr(a, 0) + reqStr(a, 1)));
    }

    // ─────────────────────────── logic / null-safety pack ───────────────────────────

    private static void logic(List<ExtensionFunction> fns) {
        fns.add(fn("logic.isNull", "logic", List.of("any"), "boolean",
                "True when the value is null / missing.",
                a -> a.isEmpty() || a.get(0) == null));

        fns.add(fn("logic.isBlank", "logic", List.of("any"), "boolean",
                "True when the value is null or an empty/whitespace string.",
                a -> {
                    Object v = a.isEmpty() ? null : a.get(0);
                    return v == null || v.toString().isBlank();
                }));

        fns.add(fn("logic.coalesce", "logic", List.of("any", "any"), "any", true,
                "First non-null argument.",
                a -> { for (Object o : a) if (o != null) return o; return null; }));
    }

    // ─────────────────────────── builders + coercion ───────────────────────────

    private static ExtensionFunction fn(String name, String pack, List<String> args, String ret,
                                        String desc, ExtensionFunction.Impl impl) {
        return new ExtensionFunction(name, pack, args, ret, true, false, desc, impl);
    }

    private static ExtensionFunction fn(String name, String pack, List<String> args, String ret,
                                        boolean variadic, String desc, ExtensionFunction.Impl impl) {
        return new ExtensionFunction(name, pack, args, ret, true, variadic, desc, impl);
    }

    static LocalDate reqDate(List<Object> args, int i) {
        Object v = args.get(i);
        if (v == null) throw new ExtensionException("expected a date at arg " + i + ", got null");
        if (v instanceof LocalDate ld) return ld;
        String s = v.toString().trim();
        // accept full ISO date-time by trimming the time portion
        int tIdx = s.indexOf('T');
        if (tIdx > 0) s = s.substring(0, tIdx);
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new ExtensionException("invalid ISO date '" + v + "' at arg " + i +
                    " (expected YYYY-MM-DD)");
        }
    }

    static long reqLong(List<Object> args, int i) {
        Object v = args.get(i);
        BigDecimal d = DecimalMath.toDecimal(v);
        if (d == null) throw new ExtensionException("expected a number at arg " + i + ", got '" + v + "'");
        return d.longValue();
    }

    @SuppressWarnings("unchecked")
    static List<Object> reqList(List<Object> args, int i) {
        Object v = args.get(i);
        if (v == null) return List.of();
        if (v instanceof List<?> l) return (List<Object>) l;
        throw new ExtensionException("expected a collection at arg " + i + ", got '" + v + "'");
    }

    static String reqStr(List<Object> args, int i) {
        Object v = args.get(i);
        if (v == null) throw new ExtensionException("expected a string at arg " + i + ", got null");
        return v.toString();
    }

    private static BigDecimal reduceNumeric(List<Object> xs, boolean min) {
        BigDecimal best = null;
        for (Object o : xs) {
            BigDecimal d = DecimalMath.toDecimal(o);
            if (d == null) continue;
            if (best == null || (min ? d.compareTo(best) < 0 : d.compareTo(best) > 0)) best = d;
        }
        return best == null ? BigDecimal.ZERO : best;
    }

    /** Thrown when an extension function is invoked with bad arguments at runtime. */
    public static final class ExtensionException extends RuntimeException {
        public ExtensionException(String message) { super(message); }
    }
}
