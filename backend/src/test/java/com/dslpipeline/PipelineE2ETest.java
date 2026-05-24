package com.dslpipeline;

import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.model.ast.AstNode;
import com.dslpipeline.model.dsl.ConditionNode;
import com.dslpipeline.model.dsl.DslAction;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.model.sl.StructuredLogic;
import com.dslpipeline.pipeline.*;
import com.dslpipeline.schema.DomainSchema;
import com.dslpipeline.service.PipelineService;
import com.dslpipeline.validator.DslValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end pipeline tests — canonical example "customer age < 18 decline the loan",
 * plus the function-expression and decimal paths.
 *
 * @author Nikunj Malik
 */
@SpringBootTest
class PipelineE2ETest {

    @Autowired NlToSlConverter nlToSl;
    @Autowired SlLinter slLinter;
    @Autowired SlToDslCompiler slToDsl;
    @Autowired DslValidator dslValidator;
    @Autowired DslToAstBuilder astBuilder;
    @Autowired TypeChecker typeChecker;
    @Autowired AstOptimizer optimizer;
    @Autowired IrBuilder irBuilder;
    @Autowired IrExecutor irExecutor;
    @Autowired PipelineService pipelineService;

    private static final String CANONICAL = "customer age < 18 decline the loan";
    private static final DomainSchema SCHEMA = DomainSchema.fromFlatMap(Map.of(
            "customer.age", "number",
            "applicant.dateOfBirth", "date",
            "loan.startDate", "date",
            "claim.amount", "decimal"));

    @Test
    void stage1_nl_to_sl() {
        StructuredLogic sl = nlToSl.convert(CANONICAL, "rule");
        assertEquals(1, sl.getClauses().size());
        assertEquals("customer.age < 18", sl.getClauses().get(0).getCondition());
        assertEquals("error", sl.getClauses().get(0).getSeverity());
        assertEquals("LOAN_DECLINED", sl.getClauses().get(0).getCode());
        assertEquals("rule", sl.getStrategy());
    }

    @Test
    void stage2_sl_lint_clean() {
        StructuredLogic sl = nlToSl.convert(CANONICAL, "rule");
        SlLinter.LintResult r = slLinter.lint(sl, SCHEMA);
        assertTrue(r.ok(), "expected clean lint, got: "
                + r.errors.stream().map(i -> i.code + " " + i.message).toList());
    }

    @Test
    void stage3_sl_to_dsl() {
        RuleDSL dsl = slToDsl.compile(nlToSl.convert(CANONICAL, "rule"));
        assertEquals(1, dsl.getConditions().size());
        assertInstanceOf(ConditionNode.LeafCondition.class, dsl.getConditions().get(0));
        ConditionNode.LeafCondition leaf = (ConditionNode.LeafCondition) dsl.getConditions().get(0);
        assertEquals("customer.age", leaf.getLeft());
        assertEquals("<", leaf.getOp());
        assertEquals(18L, ((Number) leaf.getRight()).longValue());
        assertEquals(1, dsl.getActions().size());
        assertInstanceOf(DslAction.AddErrorAction.class, dsl.getActions().get(0));
    }

    @Test
    void stage4_dsl_validation_passes() {
        RuleDSL dsl = slToDsl.compile(nlToSl.convert(CANONICAL, "rule"));
        DslValidator.Result r = dslValidator.validate(dsl);
        assertTrue(r.ok(), "expected valid DSL, got: "
                + r.errors.stream().map(i -> i.code + " " + i.message).toList());
    }

    @Test
    void stage4_dsl_validation_rejects_in_with_scalar() {
        RuleDSL dsl = new RuleDSL();
        dsl.setRuleId("bad");
        dsl.setConditions(List.of(new ConditionNode.LeafCondition("customer.region", "in", "AU")));
        dsl.setActions(List.of(new DslAction.AddErrorAction("X", "y")));
        DslValidator.Result r = dslValidator.validate(dsl);
        assertFalse(r.ok());
        assertTrue(r.errors.stream().anyMatch(i -> i.code.equals("DSL33")));
    }

    @Test
    void stage4_dsl_validation_rejects_unknown_function() {
        RuleDSL dsl = new RuleDSL();
        dsl.setRuleId("bad_fn");
        dsl.setConditions(List.of(
                new ConditionNode.LeafCondition("noSuchFunction(customer.age)", ">", 5L)));
        dsl.setActions(List.of(new DslAction.AddErrorAction("X", "y")));
        DslValidator.Result r = dslValidator.validate(dsl);
        assertFalse(r.ok());
        assertTrue(r.errors.stream().anyMatch(i -> i.code.equals("DSL35")));
    }

    @Test
    void stage5_ast_has_when_then_else() {
        RuleDSL dsl = slToDsl.compile(nlToSl.convert(CANONICAL, "rule"));
        AstNode ast = astBuilder.build(dsl);
        assertEquals("RULE", ast.getKind());
        assertEquals(3, ast.getChildren().size());
        assertEquals("WHEN", ast.getChildren().get(0).getKind());
        assertEquals("THEN", ast.getChildren().get(1).getKind());
        assertEquals("ELSE", ast.getChildren().get(2).getKind());
    }

    @Test
    void stage6_type_check_clean() {
        RuleDSL dsl = slToDsl.compile(nlToSl.convert(CANONICAL, "rule"));
        AstNode ast = astBuilder.build(dsl);
        TypeChecker.Result r = typeChecker.check(ast, SCHEMA);
        assertTrue(r.ok(), "expected clean type check, got: " + r.errors);
    }

    @Test
    void stage7_ir_canonicalisation() {
        RuleDSL dsl = slToDsl.compile(nlToSl.convert(CANONICAL, "rule"));
        CanonicalIR ir = irBuilder.build(dsl);
        assertEquals("rule", ir.getKind());
        assertEquals("<", ir.getWhen().getOp());
        assertEquals("PATH", ir.getWhen().getLhs().getOp());
        assertEquals(List.of("customer", "age"), ir.getWhen().getLhs().getPath());
        assertEquals("LIT", ir.getWhen().getRhs().getOp());
        assertEquals(1, ir.getThen().size());
        assertEquals("RAISE", ir.getThen().get(0).getOp());
        assertNotNull(ir.getNumericProfile());
        assertTrue(ir.getReferencedPaths().contains("customer.age"));
    }

    @Test
    void stage8_execute_minor_triggers_decline() {
        CanonicalIR ir = irBuilder.build(slToDsl.compile(nlToSl.convert(CANONICAL, "rule")));
        IrExecutor.ExecutionResult res = irExecutor.execute(ir,
                Map.of("customer", Map.of("age", 16)));
        assertTrue(res.conditionsMet);
        assertFalse(res.passed);
        assertEquals(1, res.errors.size());
        assertEquals("LOAN_DECLINED", res.errors.get(0).code);
        assertNotNull(res.explanation);
        assertNotNull(res.conditionTrace);
    }

    @Test
    void stage8_execute_adult_passes() {
        CanonicalIR ir = irBuilder.build(slToDsl.compile(nlToSl.convert(CANONICAL, "rule")));
        IrExecutor.ExecutionResult res = irExecutor.execute(ir,
                Map.of("customer", Map.of("age", 25)));
        assertFalse(res.conditionsMet);
        assertTrue(res.passed);
        assertEquals(0, res.errors.size());
    }

    @Test
    void endToEnd_full_pipeline_status_ok() throws Exception {
        PipelineService.Request req = new PipelineService.Request();
        req.nl = CANONICAL;
        req.strategy = "rule";
        req.schema = Map.of("customer.age", "number");
        req.payload = Map.of("customer", Map.of("age", 16));
        req.persistRunLog = false;
        req.generateScenarios = true;
        Map<String, Object> out = pipelineService.runEndToEnd(req);
        assertEquals("OK", out.get("status"));
        IrExecutor.ExecutionResult exec = (IrExecutor.ExecutionResult) out.get("stage10_execution");
        assertFalse(exec.passed);
        assertNotNull(out.get("stage11_scenarios"));
    }

    @Test
    void fromDsl_function_expression_calculateAge() throws Exception {
        RuleDSL dsl = new RuleDSL();
        dsl.setRuleId("AGE_AT_LOAN_START");
        dsl.setConditions(List.of(new ConditionNode.LeafCondition(
                "calculateAge(applicant.dateOfBirth, loan.startDate)", "<", 18L)));
        dsl.setActions(List.of(new DslAction.AddErrorAction("LOAN_DECLINED", "under 18")));

        PipelineService.Request req = new PipelineService.Request();
        req.dsl = dsl;
        req.schema = Map.of("applicant.dateOfBirth", "date", "loan.startDate", "date");
        req.payload = Map.of("applicant", Map.of("dateOfBirth", "2010-06-01"),
                "loan", Map.of("startDate", "2026-05-20"));
        req.persistRunLog = false;
        Map<String, Object> out = pipelineService.runFromDsl(req);
        assertEquals("OK", out.get("status"));
        IrExecutor.ExecutionResult exec = (IrExecutor.ExecutionResult) out.get("stage10_execution");
        assertTrue(exec.conditionsMet, "applicant aged ~15 should be under 18");
        assertFalse(exec.passed);
    }

    @Test
    void decimal_comparison_is_exact() {
        RuleDSL dsl = new RuleDSL();
        dsl.setRuleId("HIGH_VALUE");
        dsl.setConditions(List.of(new ConditionNode.LeafCondition(
                "claim.amount", ">=", new java.math.BigDecimal("10000.00"))));
        dsl.setActions(List.of(new DslAction.AddWarningAction("HIGH_VALUE", "review")));
        CanonicalIR ir = irBuilder.build(dsl);
        IrExecutor.ExecutionResult atBoundary = irExecutor.execute(ir,
                Map.of("claim", Map.of("amount", new java.math.BigDecimal("10000.00"))));
        assertTrue(atBoundary.conditionsMet);
        assertEquals(1, atBoundary.warnings.size());
    }

    @Test
    void unparseable_input_blocks_at_sl_lint() throws Exception {
        PipelineService.Request req = new PipelineService.Request();
        req.nl = "asdf qwer zxcv";
        req.strategy = "rule";
        req.persistRunLog = false;
        Map<String, Object> out = pipelineService.runEndToEnd(req);
        assertEquals("BLOCKED_AT_SL_LINT", out.get("status"));
    }
}
