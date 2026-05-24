package com.dslpipeline.controller;

import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.extensions.ExtensionFunction;
import com.dslpipeline.extensions.ExtensionRegistry;
import com.dslpipeline.llm.ClaudeClient;
import com.dslpipeline.model.ast.AstNode;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.model.sl.StructuredLogic;
import com.dslpipeline.pipeline.*;
import com.dslpipeline.repository.IrArtifactRepository;
import com.dslpipeline.schema.DomainSchema;
import com.dslpipeline.service.PipelineService;
import com.dslpipeline.testengine.ScenarioGenerator;
import com.dslpipeline.validator.DslValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API for the NL → SL → DSL → AST → IR → Runtime pipeline.
 * Every stage is individually addressable plus two whole-pipeline entry points.
 *
 * @author Nikunj Malik
 */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    private final NlToSlConverter nlToSl;
    private final SlLinter slLinter;
    private final SlToDslCompiler slToDsl;
    private final DslValidator dslValidator;
    private final DslToAstBuilder astBuilder;
    private final TypeChecker typeChecker;
    private final AstOptimizer optimizer;
    private final IrBuilder irBuilder;
    private final IrExecutor irExecutor;
    private final PipelineService pipelineService;
    private final ScenarioGenerator scenarioGenerator;
    private final IrArtifactRepository irRepo;
    private final ClaudeClient claudeClient;
    private final ExtensionRegistry registry;
    private final ObjectMapper mapper;

    public PipelineController(NlToSlConverter nlToSl, SlLinter slLinter, SlToDslCompiler slToDsl,
                              DslValidator dslValidator, DslToAstBuilder astBuilder, TypeChecker typeChecker,
                              AstOptimizer optimizer, IrBuilder irBuilder, IrExecutor irExecutor,
                              PipelineService pipelineService, ScenarioGenerator scenarioGenerator,
                              IrArtifactRepository irRepo, ClaudeClient claudeClient,
                              ExtensionRegistry registry, ObjectMapper mapper) {
        this.nlToSl = nlToSl;
        this.slLinter = slLinter;
        this.slToDsl = slToDsl;
        this.dslValidator = dslValidator;
        this.astBuilder = astBuilder;
        this.typeChecker = typeChecker;
        this.optimizer = optimizer;
        this.irBuilder = irBuilder;
        this.irExecutor = irExecutor;
        this.pipelineService = pipelineService;
        this.scenarioGenerator = scenarioGenerator;
        this.irRepo = irRepo;
        this.claudeClient = claudeClient;
        this.registry = registry;
        this.mapper = mapper;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "claudeAvailable", claudeClient.isAvailable(),
                "extensionFunctions", registry.all().size());
    }

    @GetMapping("/extensions")
    public List<Map<String, Object>> extensions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExtensionFunction f : registry.all()) {
            out.add(Map.of(
                    "name", f.getName(),
                    "pack", f.getPack(),
                    "signature", f.signature(),
                    "returnType", f.getReturnType(),
                    "description", f.getDescription()));
        }
        return out;
    }

    @PostMapping("/nl-to-sl")
    public StructuredLogic nlToSl(@RequestBody Map<String, Object> body) {
        return nlToSl.convert((String) body.get("nl"),
                (String) body.getOrDefault("strategy", "rule"));
    }

    @PostMapping("/lint-sl")
    @SuppressWarnings("unchecked")
    public SlLinter.LintResult lintSl(@RequestBody Map<String, Object> body) {
        StructuredLogic sl = mapper.convertValue(body.get("sl"), StructuredLogic.class);
        DomainSchema schema = DomainSchema.fromFlatMap((Map<String, String>) body.get("schema"));
        return slLinter.lint(sl, schema);
    }

    @PostMapping("/sl-to-dsl")
    public RuleDSL slToDsl(@RequestBody StructuredLogic sl) {
        return slToDsl.compile(sl);
    }

    @PostMapping("/validate-dsl")
    public DslValidator.Result validateDsl(@RequestBody RuleDSL dsl) {
        return dslValidator.validate(dsl);
    }

    @PostMapping("/dsl-to-ast")
    public AstNode dslToAst(@RequestBody RuleDSL dsl) {
        return astBuilder.build(dsl);
    }

    @PostMapping("/type-check")
    @SuppressWarnings("unchecked")
    public TypeChecker.Result typeCheck(@RequestBody Map<String, Object> body) {
        AstNode ast = mapper.convertValue(body.get("ast"), AstNode.class);
        DomainSchema schema = DomainSchema.fromFlatMap((Map<String, String>) body.get("schema"));
        return typeChecker.check(ast, schema);
    }

    @PostMapping("/optimize")
    public Map<String, Object> optimize(@RequestBody RuleDSL dsl) {
        AstOptimizer.Result res = optimizer.optimize(dsl);
        return Map.of("transformations", res.transformations,
                "changed", res.changed(), "optimizedDsl", dsl);
    }

    @PostMapping("/build-ir")
    public CanonicalIR buildIr(@RequestBody RuleDSL dsl) {
        return irBuilder.build(dsl);
    }

    @PostMapping("/execute-ir")
    @SuppressWarnings("unchecked")
    public IrExecutor.ExecutionResult executeIr(@RequestBody Map<String, Object> body) {
        CanonicalIR ir = mapper.convertValue(body.get("ir"), CanonicalIR.class);
        Map<String, Object> payload = (Map<String, Object>) body.get("payload");
        return irExecutor.execute(ir, payload);
    }

    @PostMapping("/generate-scenarios")
    public List<ScenarioGenerator.Scenario> generateScenarios(@RequestBody Map<String, Object> body) {
        CanonicalIR ir = mapper.convertValue(body.get("ir"), CanonicalIR.class);
        return scenarioGenerator.generate(ir);
    }

    @PostMapping("/end-to-end")
    public ResponseEntity<?> endToEnd(@RequestBody Map<String, Object> body) throws Exception {
        return ResponseEntity.ok(pipelineService.runEndToEnd(toRequest(body, false)));
    }

    @PostMapping("/from-dsl")
    public ResponseEntity<?> fromDsl(@RequestBody Map<String, Object> body) throws Exception {
        return ResponseEntity.ok(pipelineService.runFromDsl(toRequest(body, true)));
    }

    @SuppressWarnings("unchecked")
    private com.dslpipeline.service.PipelineService.Request toRequest(
            Map<String, Object> body, boolean fromDsl) {
        com.dslpipeline.service.PipelineService.Request r =
                new com.dslpipeline.service.PipelineService.Request();
        r.nl = (String) body.get("nl");
        r.strategy = (String) body.getOrDefault("strategy", "rule");
        r.schema = (Map<String, String>) body.get("schema");
        r.payload = (Map<String, Object>) body.get("payload");
        r.persistRunLog = !Boolean.FALSE.equals(body.get("persist"));
        r.generateScenarios = Boolean.TRUE.equals(body.get("generateScenarios"));
        r.tenant = (String) body.get("tenant");
        r.project = (String) body.get("project");
        r.saveAsRuleKey = (String) body.get("saveAsRuleKey");
        if (fromDsl) {
            r.dsl = mapper.convertValue(body.get("dsl"), RuleDSL.class);
        }
        return r;
    }

    @GetMapping("/artifacts")
    public Object artifacts() {
        return irRepo.findAll();
    }

    @GetMapping("/artifacts/by-rule/{ruleId}")
    public Object artifactsByRule(@PathVariable String ruleId) {
        return irRepo.findByRuleIdOrderByCompiledAtDesc(ruleId);
    }
}
