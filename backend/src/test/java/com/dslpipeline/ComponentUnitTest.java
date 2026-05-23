package com.dslpipeline;

import com.dslpipeline.extensions.ExtensionRegistry;
import com.dslpipeline.model.dsl.ConditionNode;
import com.dslpipeline.model.dsl.DslAction;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.numeric.DecimalMath;
import com.dslpipeline.numeric.NumericProfile;
import com.dslpipeline.pipeline.AstOptimizer;
import com.dslpipeline.pipeline.ConditionParser;
import com.dslpipeline.term.Term;
import com.dslpipeline.term.TermEvaluator;
import com.dslpipeline.term.TermParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fast unit tests for the deterministic components: extension functions,
 * term parser, condition parser, decimal math and the AST optimizer.
 *
 * @author Nikunj Malik
 */
class ComponentUnitTest {

    private final ExtensionRegistry registry = new ExtensionRegistry();

    // ─────────────────────────── date extension functions ───────────────────────────

    @Test
    void calculateAge_exact_birthday() {
        assertEquals(20L, registry.resolve("calculateAge")
                .invoke(List.of("2000-01-01", "2020-01-01")));
    }

    @Test
    void calculateAge_day_before_birthday_does_not_round_up() {
        assertEquals(19L, registry.resolve("calculateAge")
                .invoke(List.of("2000-01-02", "2020-01-01")));
    }

    @Test
    void calculateAge_leap_day_handled_consistently() {
        assertEquals(20L, registry.resolve("calculateAge")
                .invoke(List.of("2004-02-29", "2025-02-28")));
        assertEquals(21L, registry.resolve("calculateAge")
                .invoke(List.of("2004-02-29", "2025-03-01")));
    }

    @Test
    void compareDates_and_diffDays() {
        assertEquals(-1L, registry.resolve("compareDates")
                .invoke(List.of("2025-01-01", "2025-07-01")));
        assertEquals(0L, registry.resolve("compareDates")
                .invoke(List.of("2025-07-01", "2025-07-01")));
        assertEquals(30L, registry.resolve("diffDays")
                .invoke(List.of("2025-01-31", "2025-01-01")));
    }

    @Test
    void isBetween_inclusive_window() {
        assertEquals(true, registry.resolve("isBetween")
                .invoke(List.of("2025-02-15", "2025-01-01", "2025-03-31", true)));
        assertEquals(false, registry.resolve("isBetween")
                .invoke(List.of("2025-04-15", "2025-01-01", "2025-03-31", true)));
    }

    @Test
    void registry_has_namespaced_and_bare_names() {
        assertTrue(registry.has("date.calculateAge"));
        assertTrue(registry.has("calculateAge"));
        assertTrue(registry.has("collection.count"));
        assertTrue(registry.has("acme.riskBand"));
        assertFalse(registry.has("noSuchFunction"));
    }

    // ─────────────────────────── term parser ───────────────────────────

    @Test
    void termParser_parses_function_call() {
        Term t = TermParser.parse("calculateAge(applicant.dateOfBirth, loan.startDate)");
        assertInstanceOf(Term.CallTerm.class, t);
        Term.CallTerm c = (Term.CallTerm) t;
        assertEquals("calculateAge", c.name());
        assertEquals(2, c.args().size());
        assertInstanceOf(Term.PathTerm.class, c.args().get(0));
    }

    @Test
    void termParser_parses_path_and_literals() {
        assertInstanceOf(Term.PathTerm.class, TermParser.parse("customer.age"));
        assertInstanceOf(Term.LiteralTerm.class, TermParser.parse("18"));
        Term dec = TermParser.parse("dec(\"100.00\")");
        assertInstanceOf(Term.LiteralTerm.class, dec);
        assertEquals("dec", ((Term.LiteralTerm) dec).kind());
    }

    @Test
    void termParser_rejects_garbage() {
        assertThrows(TermParser.TermParseException.class, () -> TermParser.parse("calculateAge(("));
    }

    // ─────────────────────────── condition parser ───────────────────────────

    @Test
    void conditionParser_handles_and_with_in_list() {
        ConditionNode n = ConditionParser.parse("customer.age < 18 AND customer.region in [AU, NZ]");
        assertInstanceOf(ConditionNode.GroupCondition.class, n);
        ConditionNode.GroupCondition g = (ConditionNode.GroupCondition) n;
        assertEquals("AND", g.getOperator());
        assertEquals(2, g.getConditions().size());
        ConditionNode.LeafCondition inLeaf = (ConditionNode.LeafCondition) g.getConditions().get(1);
        assertEquals("in", inLeaf.getOp());
        assertInstanceOf(List.class, inLeaf.getRight());
    }

    @Test
    void conditionParser_handles_not_and_parentheses() {
        ConditionNode n = ConditionParser.parse(
                "(customer.age < 18) AND NOT (override.approved == true)");
        ConditionNode.GroupCondition g = (ConditionNode.GroupCondition) n;
        assertEquals("AND", g.getOperator());
        assertInstanceOf(ConditionNode.NotCondition.class, g.getConditions().get(1));
    }

    // ─────────────────────────── decimal math ───────────────────────────

    @Test
    void decimalMath_rounding_modes() {
        NumericProfile up = new NumericProfile("HALF_UP");
        NumericProfile even = new NumericProfile("HALF_EVEN");
        assertEquals(new BigDecimal("2.46"),
                DecimalMath.quantize(new BigDecimal("2.455"), 2, up));
        assertEquals(new BigDecimal("2.46"),
                DecimalMath.quantize(new BigDecimal("2.455"), 2, even));
        assertEquals(new BigDecimal("2.44"),
                DecimalMath.quantize(new BigDecimal("2.445"), 2, even));
    }

    @Test
    void decimalMath_compare_is_exact() {
        assertEquals(0, (int) DecimalMath.compare(new BigDecimal("10000.00"), 10000L));
        assertEquals(-1, (int) DecimalMath.compare(16, 18));
    }

    // ─────────────────────────── AST optimizer ───────────────────────────

    @Test
    void optimizer_constant_folds_function_and_eliminates_dead_branch() {
        TermEvaluator evaluator = new TermEvaluator(registry);
        AstOptimizer optimizer = new AstOptimizer(evaluator);

        RuleDSL dsl = new RuleDSL();
        dsl.setRuleId("FOLD");
        // calculateAge("2000-01-01","2020-01-01") = 20 ; 20 > 70 is always false
        dsl.setConditions(List.of(new ConditionNode.LeafCondition(
                "calculateAge(\"2000-01-01\", \"2020-01-01\")", ">", 70L)));
        dsl.setActions(List.of(new DslAction.AddErrorAction("X", "y")));

        AstOptimizer.Result res = optimizer.optimize(dsl);
        assertTrue(res.changed(), "expected optimizer to fold a constant");
        // a fully-false condition collapses to the FALSE sentinel: empty OR group
        ConditionNode optimized = dsl.getConditions().get(0);
        assertInstanceOf(ConditionNode.GroupCondition.class, optimized);
        ConditionNode.GroupCondition g = (ConditionNode.GroupCondition) optimized;
        assertEquals("OR", g.getOperator());
        assertTrue(g.getConditions().isEmpty());
    }

    @Test
    void optimizer_leaves_dynamic_conditions_untouched() {
        TermEvaluator evaluator = new TermEvaluator(registry);
        AstOptimizer optimizer = new AstOptimizer(evaluator);

        RuleDSL dsl = new RuleDSL();
        dsl.setRuleId("DYN");
        dsl.setConditions(List.of(new ConditionNode.LeafCondition("customer.age", "<", 18L)));
        dsl.setActions(List.of(new DslAction.AddErrorAction("X", "y")));

        AstOptimizer.Result res = optimizer.optimize(dsl);
        assertFalse(res.changed());
        assertInstanceOf(ConditionNode.LeafCondition.class, dsl.getConditions().get(0));
    }
}
