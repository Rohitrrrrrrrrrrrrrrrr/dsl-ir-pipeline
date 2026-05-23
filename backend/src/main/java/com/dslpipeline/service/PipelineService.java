package com.dslpipeline.service;

import com.dslpipeline.entity.IrArtifactEntity;
import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.model.ast.AstNode;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.model.sl.StructuredLogic;
import com.dslpipeline.pipeline.*;
import com.dslpipeline.repository.IrArtifactRepository;
import com.dslpipeline.schema.DomainSchema;
import com.dslpipeline.testengine.ScenarioGenerator;
import com.dslpipeline.validator.DslValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Orchestrates the full compiler-grade pipeline:
 *
 *   NL → SL → SL-lint → DSL → DSL-validate → AST → type-check
 *      → AST-optimise → IR → (persist) → execute → (scenario generation)
 *
 * Each stage is a gate: a blocking failure stops the pipeline and the partial
 * result is returned so the author can see exactly where it stopped.
 *
 * @author Nikunj Malik
 */
@Service
public class PipelineService {

    private final NlToSlConverter nlToSl;
    private final SlLinter slLinter;
    private final SlToDslCompiler slToDsl;
    private final DslValidator dslValidator;
    private final DslToAstBuilder astBuilder;
    private final TypeChecker typeChecker;
    private final AstOptimizer optimizer;
    private final IrBuilder irBuilder;
    private final IrExecutor irExecutor;
    private final ScenarioGenerator scenarioGenerator;
    private final IrArtifactRepository irRepo;
    private final ObjectMapper mapper;

    public PipelineService(NlToSlConverter nlToSl, SlLinter slLinter, SlToDslCompiler slToDsl,
                           DslValidator dslValidator, DslToAstBuilder astBuilder, TypeChecker typeChecker,
                           AstOptimizer optimizer, IrBuilder irBuilder, IrExecutor irExecutor,
                           ScenarioGenerator scenarioGenerator, IrArtifactRepository irRepo,
                           ObjectMapper mapper) {
        this.nlToSl = nlToSl;
        this.slLinter = slLinter;
        this.slToDsl = slToDsl;
        this.dslValidator = dslValidator;
        this.astBuilder = astBuilder;
        this.typeChecker = typeChecker;
        this.optimizer = optimizer;
        this.irBuilder = irBuilder;
        this.irExecutor = irExecutor;
        this.scenarioGenerator = scenarioGenerator;
        this.irRepo = irRepo;
        this.mapper = mapper;
    }

    /** Full NL → IR → execution pipeline. */
    public Map<String, Object> runEndToEnd(String nl, String strategy, Map<String, String> schemaMap,
                                           Map<String, Object> payload, boolean persist,
                                           boolean generateScenarios) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        DomainSchema schema = DomainSchema.fromFlatMap(schemaMap);

        // Stage 1 — NL → SL
        StructuredLogic sl = nlToSl.convert(nl, strategy);
        out.put("stage1_sl", sl);
        out.put("stage1_slRendered", sl.render());

        // Stage 2 — SL lint
        SlLinter.LintResult lint = slLinter.lint(sl, schema);
        out.put("stage2_lint", lint);
        if (!lint.ok()) {
            out.put("status", "BLOCKED_AT_SL_LINT");
            return out;
        }

        // Stage 3 — SL → DSL
        RuleDSL dsl = slToDsl.compile(sl);
        out.put("stage3_dsl", dsl);

        // Stage 4 — DSL validation
        DslValidator.Result dslVal = dslValidator.validate(dsl);
        out.put("stage4_dslValidation", dslVal);
        if (!dslVal.ok()) {
            out.put("status", "BLOCKED_AT_DSL_VALIDATION");
            return out;
        }

        // Stage 5 — DSL → AST
        AstNode ast = astBuilder.build(dsl);
        out.put("stage5_ast", ast);

        // Stage 6 — type check
        TypeChecker.Result typeRes = typeChecker.check(ast, schema);
        out.put("stage6_typeCheck", typeRes);
        if (!typeRes.ok()) {
            out.put("status", "BLOCKED_AT_TYPE_CHECK");
            return out;
        }

        // Stage 7 — AST optimisation pass
        AstOptimizer.Result optRes = optimizer.optimize(dsl);
        Map<String, Object> opt = new LinkedHashMap<>();
        opt.put("transformations", optRes.transformations);
        opt.put("changed", optRes.changed());
        opt.put("optimizedDsl", dsl);
        out.put("stage7_optimization", opt);

        // Stage 8 — IR build
        CanonicalIR ir = irBuilder.build(dsl);
        out.put("stage8_ir", ir);

        // Stage 9 — persist
        Long artifactId = persist ? persist(nl, sl, dsl, ast, ir) : null;
        out.put("stage9_artifactId", artifactId);

        // Stage 10 — execute
        IrExecutor.ExecutionResult exec = irExecutor.execute(ir, payload);
        out.put("stage10_execution", exec);

        // Stage 11 — mechanical scenario generation
        if (generateScenarios) {
            out.put("stage11_scenarios", scenarioGenerator.generate(ir));
        }

        out.put("status", "OK");
        return out;
    }

    /** DSL → IR → execution (skips NL/SL — for QA feeding DSL directly). */
    public Map<String, Object> runFromDsl(RuleDSL dsl, Map<String, String> schemaMap,
                                          Map<String, Object> payload, boolean persist,
                                          boolean generateScenarios) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        DomainSchema schema = DomainSchema.fromFlatMap(schemaMap);

        DslValidator.Result dslVal = dslValidator.validate(dsl);
        out.put("stage4_dslValidation", dslVal);
        if (!dslVal.ok()) {
            out.put("status", "BLOCKED_AT_DSL_VALIDATION");
            return out;
        }
        AstNode ast = astBuilder.build(dsl);
        out.put("stage5_ast", ast);

        TypeChecker.Result typeRes = typeChecker.check(ast, schema);
        out.put("stage6_typeCheck", typeRes);
        if (!typeRes.ok()) {
            out.put("status", "BLOCKED_AT_TYPE_CHECK");
            return out;
        }
        AstOptimizer.Result optRes = optimizer.optimize(dsl);
        Map<String, Object> opt = new LinkedHashMap<>();
        opt.put("transformations", optRes.transformations);
        opt.put("changed", optRes.changed());
        opt.put("optimizedDsl", dsl);
        out.put("stage7_optimization", opt);

        CanonicalIR ir = irBuilder.build(dsl);
        out.put("stage8_ir", ir);

        Long artifactId = persist ? persist(null, null, dsl, ast, ir) : null;
        out.put("stage9_artifactId", artifactId);

        IrExecutor.ExecutionResult exec = irExecutor.execute(ir, payload);
        out.put("stage10_execution", exec);

        if (generateScenarios) {
            out.put("stage11_scenarios", scenarioGenerator.generate(ir));
        }
        out.put("status", "OK");
        return out;
    }

    private Long persist(String nl, StructuredLogic sl, RuleDSL dsl, AstNode ast, CanonicalIR ir)
            throws Exception {
        IrArtifactEntity art = new IrArtifactEntity();
        art.setRuleId(ir.getId());
        art.setVersion(ir.getVersion());
        art.setCompiledAt(Instant.now());
        art.setOriginalNl(nl);
        art.setSlJson(sl == null ? null : mapper.writeValueAsString(sl));
        art.setDslJson(mapper.writeValueAsString(dsl));
        art.setAstJson(mapper.writeValueAsString(ast));
        art.setIrJson(mapper.writeValueAsString(ir));
        if (ir.getProvenance() != null && ir.getProvenance().get("dslHash") != null) {
            art.setDslHash(ir.getProvenance().get("dslHash").toString());
        }
        irRepo.save(art);
        return art.getId();
    }
}
