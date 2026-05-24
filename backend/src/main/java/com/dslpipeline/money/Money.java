package com.dslpipeline.money;

import java.math.BigDecimal;

/**
 * An immutable money value — a {@link BigDecimal} amount tagged with an ISO-4217
 * currency. No {@code double} ever touches this type.
 *
 * The constructor preserves the amount's scale exactly as supplied (so JSON
 * fidelity is testable); use {@link MoneyMath#normalize} to apply the currency's
 * canonical scale.
 *
 * Jackson deserialises {@code {"amount":150.00,"currency":"AUD"}} straight into
 * the canonical record constructor — because {@code amount} is typed
 * {@code BigDecimal}, the number token's textual scale is preserved (no double
 * leakage).
 *
 * @author Nikunj Malik
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Money.amount is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Money.currency is required");
        }
        currency = currency.trim().toUpperCase();
    }

    /** Build from a decimal string — never from a double. */
    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    /** A normalised zero in the given currency (e.g. {@code 0.00 AUD}). */
    public static Money zero(String currency) {
        return new Money(
                BigDecimal.ZERO.setScale(CurrencyProfile.scaleOf(currency)), currency);
    }

    /** Scale (number of decimal places) of the held amount. */
    public int scale() {
        return amount.scale();
    }

    public boolean sameCurrency(Money other) {
        return other != null && currency.equals(other.currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
