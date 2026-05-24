package com.dslpipeline;

import com.dslpipeline.money.CurrencyMismatchException;
import com.dslpipeline.money.Money;
import com.dslpipeline.money.MoneyMath;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Money conformance suite — the mandatory deploy-gate.
 *
 * Every test asserts the four money invariants: numeric correctness, scale
 * correctness, HALF_EVEN rounding correctness, and currency safety. If any test
 * here fails, the engine must not be deployed.
 *
 * @author Nikunj Malik
 */
class MoneyConformanceTest {

    /**
     * A mapper that preserves decimal fidelity. {@code USE_BIG_DECIMAL_FOR_FLOATS}
     * guarantees JSON floats become {@link BigDecimal} (never double); BigDecimal
     * serialisation already preserves scale via {@code toString()}.
     */
    private static ObjectMapper mapper() {
        ObjectMapper m = new ObjectMapper();
        m.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        return m;
    }

    // ── 1. Parsing & serialization (JSON ↔ BigDecimal) ──

    @Test
    void s1_1_json_decimal_fidelity_no_double_leakage() throws Exception {
        Money money = mapper().readValue(
                "{ \"amount\": 0.1, \"currency\": \"AUD\" }", Money.class);
        assertEquals(new BigDecimal("0.1"), money.amount());
    }

    @Test
    void s1_2_trailing_scale_preserved_on_parse() throws Exception {
        Money money = mapper().readValue(
                "{ \"amount\": 150.00, \"currency\": \"AUD\" }", Money.class);
        assertEquals(2, money.amount().scale());
    }

    // ── 2. Scale & currency invariants ──

    @Test
    void s2_1_currency_default_scale_enforced() {
        Money normalized = MoneyMath.normalize(Money.of(new BigDecimal("10"), "AUD"));
        assertEquals(new BigDecimal("10.00"), normalized.amount());
        assertEquals(2, normalized.scale());
    }

    @Test
    void s2_2_zero_money_normalisation() {
        Money normalized = MoneyMath.normalize(Money.of(BigDecimal.ZERO, "AUD"));
        assertEquals(new BigDecimal("0.00"), normalized.amount());
    }

    // ── 3. Addition & subtraction ──

    @Test
    void s3_1_simple_subtraction() {
        Money result = MoneyMath.subtract(Money.of("150.00", "AUD"), Money.of("75.25", "AUD"));
        assertEquals("74.75", result.amount().toPlainString());
        assertEquals(2, result.scale());
    }

    @Test
    void s3_2_negative_floor_rule() {
        Money result = MoneyMath.subtractFloorZero(
                Money.of("50.00", "AUD"), Money.of("75.00", "AUD"));
        assertTrue(result.amount().compareTo(BigDecimal.ZERO) >= 0, "money is never negative");
        assertEquals("0.00", result.amount().toPlainString());
    }

    // ── 4. Percentage & multiplication (HALF_EVEN rounding traps) ──

    @Test
    void s4_1_classic_rounding_trap_1_005() {
        Money result = MoneyMath.multiply(Money.of("1.005", "AUD"), new BigDecimal("1.00"));
        assertEquals("1.00", result.amount().toPlainString(), "HALF_EVEN: 1.005 → 1.00");
    }

    @Test
    void s4_2_rounding_trap_2_675() {
        Money result = MoneyMath.multiply(Money.of("2.675", "AUD"), new BigDecimal("1.00"));
        assertEquals("2.68", result.amount().toPlainString(), "HALF_EVEN: 2.675 → 2.68");
    }

    @Test
    void s4_3_percentage_application() {
        // 10% of 120.00 = 12.00
        Money result = MoneyMath.percentage(Money.of("120.00", "AUD"), new BigDecimal("10"));
        assertEquals("12.00", result.amount().toPlainString());
    }

    // ── 5. Aggregation (line → claim) ──

    @Test
    void s5_1_sum_multiple_lines() {
        Money total = MoneyMath.sum(List.of(
                Money.of("10.10", "AUD"), Money.of("20.20", "AUD"), Money.of("30.30", "AUD")));
        assertEquals("60.60", total.amount().toPlainString());
        assertEquals(2, total.scale());
    }

    @Test
    void s5_2_sum_with_mixed_input_scales() {
        Money total = MoneyMath.sum(List.of(
                Money.of("10.1", "AUD"), Money.of("20.20", "AUD"), Money.of("30", "AUD")));
        assertEquals("60.30", total.amount().toPlainString());
    }

    // ── 6. Defaults & null handling ──

    @Test
    void s6_1_missing_money_defaults_to_zero() {
        Money benefit = MoneyMath.normalizeOrZero(null, "AUD");
        assertEquals(new BigDecimal("0.00"), benefit.amount());
        assertEquals("AUD", benefit.currency());
    }

    // ── 7. Currency safety ──

    @Test
    void s7_1_mixed_currency_addition_rejected() {
        assertThrows(CurrencyMismatchException.class, () ->
                MoneyMath.add(Money.of("10.00", "AUD"), Money.of("10.00", "USD")));
    }

    @Test
    void s7_2_mixed_currency_subtraction_rejected() {
        assertThrows(CurrencyMismatchException.class, () ->
                MoneyMath.subtract(Money.of("10.00", "AUD"), Money.of("10.00", "USD")));
    }

    // ── 8. Ordering & idempotency ──

    @Test
    void s8_1_operation_ordering_invariance() {
        Money a = Money.of("100.00", "AUD");
        Money b = Money.of("40.00", "AUD");
        BigDecimal r = new BigDecimal("1.25");
        Money left = MoneyMath.multiply(MoneyMath.subtract(a, b), r);          // (A−B)×R
        Money right = MoneyMath.subtract(MoneyMath.multiply(a, r),
                MoneyMath.multiply(b, r));                                     // A×R − B×R
        assertEquals(left.amount(), right.amount());
    }

    @Test
    void s8_2_idempotent_normalisation() {
        Money once = MoneyMath.normalize(Money.of("12.3456", "AUD"));
        Money twice = MoneyMath.normalize(once);
        assertEquals(once.amount(), twice.amount());
    }

    // ── 9. Large-number stress ──

    @Test
    void s9_1_high_value_multiplication() {
        Money result = MoneyMath.multiply(
                Money.of("9999999.99", "AUD"), new BigDecimal("0.15"));
        assertEquals("1500000.00", result.amount().toPlainString());
        assertEquals(2, result.scale());
    }

    // ── 10. Serialization round-trip ──

    @Test
    void s10_1_json_round_trip_preserves_value_scale_currency() throws Exception {
        ObjectMapper m = mapper();
        Money original = m.readValue(
                "{ \"amount\": 150.00, \"currency\": \"AUD\" }", Money.class);
        String json = m.writeValueAsString(original);
        Money roundTripped = m.readValue(json, Money.class);
        assertEquals(original, roundTripped);
        assertEquals(2, roundTripped.scale());
        assertEquals("AUD", roundTripped.currency());
    }

    // ── zero-decimal currency profile ──

    @Test
    void zero_decimal_currency_jpy_has_scale_zero() {
        Money jpy = MoneyMath.normalize(Money.of("1000", "JPY"));
        assertEquals(0, jpy.scale(), "JPY is a zero-decimal currency");
        assertEquals("1000", jpy.amount().toPlainString());
    }
}
