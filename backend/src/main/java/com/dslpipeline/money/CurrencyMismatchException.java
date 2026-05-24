package com.dslpipeline.money;

/**
 * Thrown when an arithmetic operation is attempted across two different
 * currencies. Cross-currency math is never silent — it is a hard failure.
 *
 * @author Nikunj Malik
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String left, String right) {
        super("Cross-currency operation rejected: " + left + " vs " + right
                + " — currencies must match (no implicit conversion).");
    }
}
