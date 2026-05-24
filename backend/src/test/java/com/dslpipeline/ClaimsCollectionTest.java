package com.dslpipeline;

import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.model.dsl.ConditionNode;
import com.dslpipeline.model.dsl.DslAction;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.pipeline.IrBuilder;
import com.dslpipeline.validator.DslValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Claims collection regression pack — exercises the collection rule patterns
 * from the QA matrix that the engine evaluates deterministically today:
 * existence (ANY), absence (NONE), universal (ALL) and counting (COUNT WHERE).
 *
 * Each rule is validated, compiled to IR and executed against canonical
 * trigger / no-trigger claim payloads, so any drift in collection semantics is
 * immediately visible.
 *
 * @author Nikunj Malik
 */
@SpringBootTest
class ClaimsCollectionTest {

    @Autowired IrBuilder irBuilder;
    @Autowired IrExecutor irExecutor;
    @Autowired DslValidator dslValidator;

    private static final String LINES = "claim.claimLines";

    // ── ZR_COLL_1_ANY — any uncovered line → flag the claim ──

    @Test
    void zrColl1_any_uncovered_line_flags_claim() {
        RuleDSL rule = anyRule("ZR_COLL_1_ANY",
                leaf("line.isCovered", "==", false),
                ensure("claim.reviewRequired", true));
        assertValid(rule);

        IrExecutor.ExecutionResult fired = run(rule, claim(
                line(true, 100, 50, "General"),
                line(false, 80, 0, "General")));
        assertTrue(fired.conditionsMet, "an uncovered line exists → ANY is true");
        assertEquals(Boolean.TRUE, dig(fired, "claim", "reviewRequired"));

        IrExecutor.ExecutionResult quiet = run(rule, claim(
                line(true, 100, 50, "General"),
                line(true, 80, 40, "General")));
        assertFalse(quiet.conditionsMet, "all lines covered → ANY is false");
        assertNull(dig(quiet, "claim", "reviewRequired"));
    }

    // ── ZR_COLL_2_NONE — no line with benefit > 0 → reject ──

    @Test
    void zrColl2_none_with_benefit_rejects_claim() {
        RuleDSL rule = new RuleDSL();
        rule.setRuleId("ZR_COLL_2_NONE");
        rule.setConditions(List.of(new ConditionNode.NotCondition(
                exists(leaf("line.benefitAmount", ">", 0)))));
        rule.setActions(List.of(ensure("claim.claimStatus", "Rejected")));
        assertValid(rule);

        IrExecutor.ExecutionResult fired = run(rule, claim(
                line(false, 100, 0, "General"),
                line(false, 80, 0, "General")));
        assertTrue(fired.conditionsMet, "no line has benefit > 0 → NONE is true");
        assertEquals("Rejected", dig(fired, "claim", "claimStatus"));

        IrExecutor.ExecutionResult quiet = run(rule, claim(
                line(true, 100, 50, "General")));
        assertFalse(quiet.conditionsMet, "a line has benefit > 0 → NONE is false");
    }

    // ── ZR_COLL_3_EXISTS — any line charged > 500 → flag ──

    @Test
    void zrColl3_exists_high_charge_flags_claim() {
        RuleDSL rule = anyRule("ZR_COLL_3_EXISTS",
                leaf("line.chargedAmount", ">", 500),
                ensure("claim.reviewRequired", true));
        assertValid(rule);

        assertTrue(run(rule, claim(line(true, 600, 0, "General"))).conditionsMet);
        assertFalse(run(rule, claim(line(true, 200, 0, "General"))).conditionsMet);
    }

    // ── R6 — ALL lines charged <= 1000 → auto-approve ──

    @Test
    void r6_universal_all_lines_within_bound() {
        RuleDSL rule = new RuleDSL();
        rule.setRuleId("R6_ALL_WITHIN_BOUND");
        ConditionNode.ForAllCondition forAll = new ConditionNode.ForAllCondition();
        forAll.setCollection(LINES);
        forAll.setAs("line");
        forAll.setCondition(leaf("line.chargedAmount", "<=", 1000));
        rule.setConditions(List.of(forAll));
        rule.setActions(List.of(ensure("claim.claimStatus", "Approved")));
        assertValid(rule);

        IrExecutor.ExecutionResult ok = run(rule, claim(
                line(true, 900, 0, "General"), line(true, 1000, 0, "General")));
        assertTrue(ok.conditionsMet, "every line is within bound → ALL is true");
        assertEquals("Approved", dig(ok, "claim", "claimStatus"));

        IrExecutor.ExecutionResult breach = run(rule, claim(
                line(true, 900, 0, "General"), line(true, 1500, 0, "General")));
        assertFalse(breach.conditionsMet, "one line breaches → ALL is false");
    }

    // ── R7 — COUNT of covered lines == 0 → reject ──

    @Test
    void r7_count_covered_equals_zero_rejects() {
        RuleDSL rule = countRule("R7_NO_COVERED",
                leaf("line.isCovered", "==", true), "==", 0,
                ensure("claim.claimStatus", "Rejected"));
        assertValid(rule);

        assertTrue(run(rule, claim(line(false, 100, 0, "G"), line(false, 80, 0, "G")))
                .conditionsMet, "zero covered lines → count == 0");
        assertFalse(run(rule, claim(line(true, 100, 50, "G"), line(false, 80, 0, "G")))
                .conditionsMet, "one covered line → count != 0");
    }

    // ── R8 — COUNT of uncovered lines > 2 → escalate ──

    @Test
    void r8_count_uncovered_over_threshold_escalates() {
        RuleDSL rule = countRule("R8_MANY_UNCOVERED",
                leaf("line.isCovered", "==", false), ">", 2,
                ensure("claim.escalated", true));
        assertValid(rule);

        IrExecutor.ExecutionResult fired = run(rule, claim(
                line(false, 1, 0, "G"), line(false, 2, 0, "G"),
                line(false, 3, 0, "G"), line(true, 4, 0, "G")));
        assertTrue(fired.conditionsMet, "3 uncovered lines → count 3 > 2");
        assertEquals(Boolean.TRUE, dig(fired, "claim", "escalated"));

        assertFalse(run(rule, claim(line(false, 1, 0, "G"), line(false, 2, 0, "G")))
                .conditionsMet, "2 uncovered lines → count 2 is not > 2");
    }

    // ── mixed claim — covered + uncovered, ANY uncovered still fires ──

    @Test
    void mixed_claim_any_uncovered_still_fires() {
        RuleDSL rule = anyRule("MIXED_ANY",
                leaf("line.isCovered", "==", false),
                ensure("claim.reviewRequired", true));
        IrExecutor.ExecutionResult r = run(rule, claim(
                line(true, 100, 50, "Hospital"),
                line(true, 200, 80, "Dental"),
                line(false, 300, 0, "Dental")));
        assertTrue(r.conditionsMet);
        assertEquals(Boolean.TRUE, dig(r, "claim", "reviewRequired"));
    }

    // ── empty collection edge cases ──

    @Test
    void empty_collection_any_is_false_all_is_true() {
        RuleDSL anyRule = anyRule("EMPTY_ANY",
                leaf("line.isCovered", "==", false), ensure("claim.flag", true));
        assertFalse(run(anyRule, claim()).conditionsMet,
                "ANY over an empty collection is false");

        RuleDSL allRule = new RuleDSL();
        allRule.setRuleId("EMPTY_ALL");
        ConditionNode.ForAllCondition forAll = new ConditionNode.ForAllCondition();
        forAll.setCollection(LINES);
        forAll.setAs("line");
        forAll.setCondition(leaf("line.chargedAmount", "<=", 1000));
        allRule.setConditions(List.of(forAll));
        allRule.setActions(List.of(ensure("claim.claimStatus", "Approved")));
        assertTrue(run(allRule, claim()).conditionsMet,
                "ALL over an empty collection is vacuously true");
    }

    // ─────────────────────────── helpers ───────────────────────────

    private void assertValid(RuleDSL rule) {
        DslValidator.Result v = dslValidator.validate(rule);
        assertTrue(v.ok(), () -> "DSL invalid: "
                + v.errors.stream().map(i -> i.code + " " + i.message).toList());
    }

    private IrExecutor.ExecutionResult run(RuleDSL rule, Map<String, Object> payload) {
        CanonicalIR ir = irBuilder.build(rule);
        return irExecutor.execute(ir, payload);
    }

    private RuleDSL anyRule(String id, ConditionNode.LeafCondition where, DslAction action) {
        RuleDSL rule = new RuleDSL();
        rule.setRuleId(id);
        rule.setConditions(List.of(exists(where)));
        rule.setActions(List.of(action));
        return rule;
    }

    private RuleDSL countRule(String id, ConditionNode.LeafCondition where,
                              String op, int value, DslAction action) {
        RuleDSL rule = new RuleDSL();
        rule.setRuleId(id);
        ConditionNode.CountWhereCondition cw = new ConditionNode.CountWhereCondition();
        cw.setCollection(LINES);
        cw.setAs("line");
        cw.setCondition(where);
        cw.setOp(op);
        cw.setValue(value);
        rule.setConditions(List.of(cw));
        rule.setActions(List.of(action));
        return rule;
    }

    private ConditionNode.ExistsCondition exists(ConditionNode condition) {
        ConditionNode.ExistsCondition e = new ConditionNode.ExistsCondition();
        e.setCollection(LINES);
        e.setAs("line");
        e.setCondition(condition);
        return e;
    }

    private ConditionNode.LeafCondition leaf(String left, String op, Object right) {
        return new ConditionNode.LeafCondition(left, op, right);
    }

    private DslAction ensure(String path, Object value) {
        return new DslAction.EnsureAction(path, value);
    }

    private Map<String, Object> line(boolean covered, Object charged,
                                     Object benefit, String item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isCovered", covered);
        m.put("chargedAmount", charged);
        m.put("benefitAmount", benefit);
        m.put("itemType", item);
        return m;
    }

    @SafeVarargs
    private Map<String, Object> claim(Map<String, Object>... lines) {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("claimLines", new ArrayList<>(List.of(lines)));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("claim", claim);
        return root;
    }

    @SuppressWarnings("unchecked")
    private Object dig(IrExecutor.ExecutionResult r, String entity, String field) {
        Object e = r.outputPayload.get(entity);
        return e instanceof Map<?, ?> m ? ((Map<String, Object>) m).get(field) : null;
    }
}
