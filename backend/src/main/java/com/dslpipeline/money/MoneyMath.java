package com.dslpipeline.money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Currency-safe, decimal-safe money arithmetic.
 *
 * Invariants enforced by every operation:
 *   - Numeric correctness — exact {@link BigDecimal} maths, no {@code double}.
 *   - Scale correctness   — results carry the currency's canonical scale.
 *   - Rounding correctness— {@link CurrencyProfile#ROUNDING} (HALF_EVEN).
 *   - Currency safety     — operands must share a currency, else a hard failure.
 *
 * @author Nikunj Malik
 */
public final class MoneyMath {

    private MoneyMath() {}

    /** Apply the currency's canonical scale + HALF_EVEN rounding. Idempotent. */
    public static Money normalize(Money m) {
        if (m == null) {
            throw new IllegalArgumentException("cannot normalize a null Money");
        }
        int scale = CurrencyProfile.scaleOf(m.currency());
        BigDecimal scaled = m.amount().setScale(scale, CurrencyProfile.ROUNDING);
        return new Money(scaled, m.currency());
    }

    /** Normalise a possibly-missing money value to zero in the given currency. */
    public static Money normalizeOrZero(Money m, String currency) {
        return m == null ? Money.zero(currency) : normalize(m);
    }

    public static Money add(Money a, Money b) {
        requireSameCurrency(a, b);
        return normalize(new Money(a.amount().add(b.amount()), a.currency()));
    }

    public static Money subtract(Money a, Money b) {
        requireSameCurrency(a, b);
        return normalize(new Money(a.amount().subtract(b.amount()), a.currency()));
    }

    /** Subtraction floored at zero — money is never negative (no negative payouts). */
    public static Money subtractFloorZero(Money a, Money b) {
        Money r = subtract(a, b);
        return r.amount().signum() < 0 ? Money.zero(r.currency()) : r;
    }

    /** Multiply by an exact decimal factor, then normalise (HALF_EVEN). */
    public static Money multiply(Money m, BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("multiply factor is required");
        }
        // BigDecimal.multiply is exact; normalize() does the single, final rounding
        return normalize(new Money(m.amount().multiply(factor), m.currency()));
    }

    /** Apply a percentage (e.g. {@code 10} → 10%). pct/100 is always exact. */
    public static Money percentage(Money m, BigDecimal pct) {
        if (pct == null) {
            throw new IllegalArgumentException("percentage is required");
        }
        return multiply(m, pct.movePointLeft(2));
    }

    /** Currency-safe sum. Each element is normalised; the running total too. */
    public static Money sum(List<Money> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("sum requires at least one Money value");
        }
        Money total = normalize(items.get(0));
        for (int i = 1; i < items.size(); i++) {
            total = add(total, items.get(i));
        }
        return total;
    }

    /** Hard-fail any cross-currency operation. */
    public static void requireSameCurrency(Money a, Money b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("money operand is null");
        }
        if (!a.currency().equals(b.currency())) {
            throw new CurrencyMismatchException(a.currency(), b.currency());
        }
    }
}
