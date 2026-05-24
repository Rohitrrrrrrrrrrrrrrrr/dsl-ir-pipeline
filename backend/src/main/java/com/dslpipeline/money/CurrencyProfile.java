package com.dslpipeline.money;

import java.math.RoundingMode;
import java.util.Map;

/**
 * Per-currency numeric profile — the single source of truth for how many
 * decimal places a currency carries and how money is rounded.
 *
 * Rounding is {@link RoundingMode#HALF_EVEN} ("banker's rounding") for ALL
 * currencies — this is the non-negotiable money rounding mode: it removes the
 * systematic upward bias of HALF_UP across millions of transactions.
 *
 * @author Nikunj Malik
 */
public final class CurrencyProfile {

    private CurrencyProfile() {}

    /** The money rounding mode. Never HALF_UP for money. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    /** Minor-unit scale per ISO-4217 currency. */
    private static final Map<String, Integer> SCALE = Map.ofEntries(
            Map.entry("AUD", 2), Map.entry("USD", 2), Map.entry("NZD", 2),
            Map.entry("GBP", 2), Map.entry("EUR", 2), Map.entry("SGD", 2),
            Map.entry("CAD", 2), Map.entry("HKD", 2), Map.entry("CHF", 2),
            Map.entry("JPY", 0), Map.entry("KRW", 0),                     // zero-decimal
            Map.entry("BHD", 3), Map.entry("KWD", 3), Map.entry("OMR", 3) // three-decimal
    );

    private static final int DEFAULT_SCALE = 2;

    /** Decimal places for a currency (default 2 for unknown codes). */
    public static int scaleOf(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency code is required");
        }
        return SCALE.getOrDefault(currency.trim().toUpperCase(), DEFAULT_SCALE);
    }

    public static boolean isKnown(String currency) {
        return currency != null && SCALE.containsKey(currency.trim().toUpperCase());
    }
}
