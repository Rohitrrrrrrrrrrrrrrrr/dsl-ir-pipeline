package com.dslpipeline.service;

import com.dslpipeline.entity.IrArtifactEntity;
import com.dslpipeline.entity.RuleDefinitionEntity;
import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.model.ast.AstNode;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.model.sl.StructuredLogic;
import com.dslpipeline.observability.PipelineMetrics;
import com.dslpipeline.pipeline.*;
import com.dslpipeline.repository.IrArtifactRepository;
import com.dslpipeline.schema.DomainSchema;
import com.dslpipeline.testengine.ScenarioGenerator;
import com.dslpipeline.validator.DslValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Orchestrates the full compiler-grade pipeline and the data layer around it:
 *
 *   NL → SL → SL-lint → DSL → DSL-validate → AST → type-check
 *      → AST-optimise → IR → (persist run + optionally save rule) → execute
 *
 * Every run is instrumented via {@link PipelineMetrics} — duration, status,
 * stage blocks, validation and execution outcomes feed {@code /actuator/prometheus}.
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
    private final IrArtifactRepository runLogRepo;
    private final RuleRepositoryService ruleRepository;
    private final SchemaService schemaService;
    private final DataBagService dataBagService;
    private final PipelineMetrics metrics;
    private final ObjectMapper mapper;

    public PipelineService(NlToSlConverter nlToSl, SlLinter slLinter, SlToDslCompiler slToDsl,
                           DslValidator dslValidator, DslToAstBuilder astBuilder, TypeChecker typeChecker,
                           AstOptimizer optimizer, IrBuilder irBuilder, IrExecutor irExecutor,
                           ScenarioGenerator scenarioGenerator, IrArtifactRepository runLogRepo,
                           RuleRepositoryService ruleRepository, SchemaService schemaService,
                           DataBagService dataBagService, PipelineMetrics metrics, ObjectMapper mapper) {
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
        this.runLogRepo = runLogRepo;
        this.ruleRepository = ruleRepository;
        this.schemaService = schemaService;
        this.dataBagService = dataBagService;
        this.metrics = metrics;
        this.mapper = mapper;
    }

    /** Request envelope for a pipeline run. */
    public static class Request {
        public String nl;
        public String strategy = "rule";
        public Map<String, String> schema;
        public Map<String, Object> payload;
        public boolean persistRunLog = true;
        public boolean generateScenarios = false;
        public String tenant;
        public String project;
        public String saveAsRuleKey;     // when set, persists to the rule_definition tables
        public RuleDSL dsl;              // for the from-DSL entry point
    }

    /** Full NL → IR → execution pipeline. */
    public Map<String, Object> runEndToEnd(Request req) throws Exception {
        Timer.Sample sample = metrics.startTimer();
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, String> schemaMap = effectiveSchema(req);
            DomainSchema schema = DomainSchema.fromFlatMap(schemaMap);
            out.put("resolvedSchema", schemaMap);

            // Stage 1 — NL → SL
            StructuredLogic sl = nlToSl.convert(req.nl, req.strategy);
            out.put("stage1_sl", sl);
            out.put("stage1_slRendered", sl.render());

            // Stage 2 — SL lint
            SlLinter.LintResult lint = slLinter.lint(sl, schema);
            out.put("stage2_lint", lint);
            if (!lint.ok()) {
                metrics.recordStageBlocked("SL_LINT");
                out.put("status", "BLOCKED_AT_SL_LINT");
                return out;
            }

            // Stage 3 — SL → DSL
            RuleDSL dsl = slToDsl.compile(sl);
            out.put("stage3_dsl", dsl);

            return compileFromDsl(dsl, schema, req, out, sl, req.nl);
        } finally {
            metrics.stopTimer(sample);
            metrics.recordPipelineRun(String.valueOf(out.getOrDefault("status", "ERROR")));
        }
    }

    /** DSL → IR → execution (skips NL/SL). */
    public Map<String, Object> runFromDsl(Request req) throws Exception {
        Timer.Sample sample = metrics.startTimer();
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, String> schemaMap = effectiveSchema(req);
            DomainSchema schema = DomainSchema.fromFlatMap(schemaMap);
            out.put("resolvedSchema", schemaMap);
            return compileFromDsl(req.dsl, schema, req, out, null, null);
        } finally {
            metrics.stopTimer(sample);
            metrics.recordPipelineRun(String.valueOf(out.getOrDefault("status", "ERROR")));
        }
    }

    // ─────────────────────────── shared tail (stages 4-11) ───────────────────────────

    private Map<String, Object> compileFromDsl(RuleDSL dsl, DomainSchema schema, Request req,
                                               Map<String, Object> out, StructuredLogic sl,
                                               String nl) throws Exception {
        // Stage 4 — DSL validation
        DslValidator.Result dslVal = dslValidator.validate(dsl);
        out.put("stage4_dslValidation", dslVal);
        metrics.recordDslValidation(dslVal.ok());
        if (!dslVal.ok()) {
            metrics.recordStageBlocked("DSL_VALIDATION");
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
            metrics.recordStageBlocked("TYPE_CHECK");
            out.put("status", "BLOCKED_AT_TYPE_CHECK");
            return out;
        }

        // Stage 7 — AST optimisation
        AstOptimizer.Result optRes = optimizer.optimize(dsl);
        Map<String, Object> opt = new LinkedHashMap<>();
        opt.put("transformations", optRes.transformations);
        opt.put("changed", optRes.changed());
        opt.put("optimizedDsl", dsl);
        out.put("stage7_optimization", opt);

        // Stage 8 — IR build
        CanonicalIR ir = irBuilder.build(dsl);
        out.put("stage8_ir", ir);

        // Stage 8b — persist authored rule (faithful 3-table model)
        if (req.saveAsRuleKey != null && !req.saveAsRuleKey.isBlank()
                && req.tenant != null && req.project != null) {
            RuleDefinitionEntity rd = ruleRepository.save(req.tenant, req.project, "default",
                    req.saveAsRuleKey, ir.getId(), dsl, ir);
            Map<String, Object> saved = new LinkedHashMap<>();
            saved.put("ruleId", rd.getRuleId());
            saved.put("ruleKey", rd.getRuleKey());
            saved.put("ruleUuid", rd.getRuleUuid());
            saved.put("version", rd.getVersion());
            saved.put("status", rd.getStatus());
            out.put("stage8b_savedRule", saved);
            metrics.recordRuleSaved();
        }

        // Stage 9 — persist run log
        Long runLogId = req.persistRunLog ? persistRunLog(nl, sl, dsl, ast, ir) : null;
        out.put("stage9_runLogId", runLogId);

        // Stage 10 — execute (databag-seeded)
        Map<String, Object> payload = req.tenant == null
                ? req.payload
                : dataBagService.seedInto(req.payload, req.tenant, req.project);
        IrExecutor.ExecutionResult exec = irExecutor.execute(ir, payload);
        out.put("stage10_execution", exec);
        metrics.recordExecution(exec.passed);

        // Stage 11 — mechanical scenario generation
        if (req.generateScenarios) {
            out.put("stage11_scenarios", scenarioGenerator.generate(ir));
        }

        out.put("status", "OK");
        return out;
    }

    /** Resolve the effective schema: explicit request schema wins, else config_schema. */
    private Map<String, String> effectiveSchema(Request req) {
        if (req.schema != null && !req.schema.isEmpty()) return req.schema;
        if (req.tenant != null && req.project != null) {
            return schemaService.resolveActiveSchema(req.tenant, req.project);
        }
        return Map.of();
    }

    private Long persistRunLog(String nl, StructuredLogic sl, RuleDSL dsl,
                               AstNode ast, CanonicalIR ir) throws Exception {
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
        runLogRepo.save(art);
        return art.getId();
    }
}
