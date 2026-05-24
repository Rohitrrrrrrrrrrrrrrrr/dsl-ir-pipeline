package com.dslpipeline.llm;

import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.sl.StructuredLogic;
import com.dslpipeline.observability.PipelineMetrics;
import com.dslpipeline.validator.DslValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-intent LLM service — the assist layer.
 *
 * Faithful to the ZenLogIQ design: the LLM TRANSLATES, PROPOSES and EXPLAINS;
 * it never executes rules or makes the decision. Every LLM output is handed
 * back to the deterministic pipeline (validator / executor) as the gatekeeper.
 *
 * Intents: normalize_nl, translate_dsl, structured_to_nl, fix_dsl, explain_decision.
 *
 * @author Nikunj Malik
 */
@Service
public class LlmService {

    private static final int MAX_HEAL_ATTEMPTS = 3;

    private final ClaudeClient claude;
    private final AiModelPolicy modelPolicy;
    private final DslValidator dslValidator;
    private final PipelineMetrics metrics;
    private final ObjectMapper mapper;

    public LlmService(ClaudeClient claude, AiModelPolicy modelPolicy,
                      DslValidator dslValidator, PipelineMetrics metrics, ObjectMapper mapper) {
        this.claude = claude;
        this.modelPolicy = modelPolicy;
        this.dslValidator = dslValidator;
        this.metrics = metrics;
        this.mapper = mapper;
    }

    public boolean isAvailable() {
        return claude.isAvailable();
    }

    // ─────────────────────────── normalize_nl ───────────────────────────

    public StructuredLogic normalizeNl(String nl) throws Exception {
        metrics.recordLlmCall(LlmIntent.NORMALIZE_NL.key());
        return claude.normalizeToSl(nl);
    }

    // ─────────────────────────── translate_dsl ───────────────────────────

    /** Structured Logic → RuleDSL JSON. */
    public RuleDSL translateToDsl(String structuredLogic, String schemaJson) throws Exception {
        String system = """
            You compile Structured Logic into RuleDSL JSON. Output ONLY valid JSON.
            RuleDSL shape:
            { "ruleId": "...", "priority": 100,
              "conditions": [ <ConditionNode> ],
              "actions": [ <DslAction> ], "elseActions": [ <DslAction> ],
              "haltOnViolation": true, "roundingMode": "HALF_UP" }
            ConditionNode: a leaf is { "type":"leaf", "left":"path|fn(...)", "op":"< <= > >= == != in 'not in'", "right": <value> }.
            Groups: { "type":"group", "operator":"AND|OR", "conditions":[...] }.
            DslAction: { "type":"addError|addWarning|ensure", "code":"...", "message":"..." }
            or { "type":"ensure", "path":"result.x", "value": <v> }.
            Non-leaf nodes REQUIRE the "type" discriminator.
            """;
        String user = "Schema:\n" + schemaJson + "\n\nStructured Logic:\n" + structuredLogic;
        metrics.recordLlmCall(LlmIntent.TRANSLATE_DSL.key());
        String text = claude.complete(modelPolicy.resolveModel(LlmIntent.TRANSLATE_DSL), system, user);
        return mapper.readValue(claude.extractJson(text), RuleDSL.class);
    }

    // ─────────────────────────── structured_to_nl ───────────────────────────

    /** RuleDSL JSON → plain-English narration. */
    public String narrate(String dslJson) throws Exception {
        String system = "You restate a RuleDSL as one clear plain-English sentence for a "
                + "business reader. Output prose only — no JSON, no code.";
        metrics.recordLlmCall(LlmIntent.STRUCTURED_TO_NL.key());
        return claude.complete(modelPolicy.resolveModel(LlmIntent.STRUCTURED_TO_NL),
                system, "RuleDSL:\n" + dslJson).trim();
    }

    // ─────────────────────────── fix_dsl (self-heal) ───────────────────────────

    /** One-shot repair: hand an invalid DSL + its errors to the LLM. */
    public RuleDSL repairDsl(String badDslJson, List<String> errors) throws Exception {
        String system = """
            You repair an invalid RuleDSL JSON. Output ONLY the corrected JSON.
            Keep the author's intent; only fix what the validation errors describe.
            Non-leaf nodes need a "type" discriminator; 'in'/'not in' need a JSON array.
            """;
        String user = "Validation errors:\n- " + String.join("\n- ", errors)
                + "\n\nInvalid RuleDSL:\n" + badDslJson;
        metrics.recordLlmCall(LlmIntent.FIX_DSL.key());
        String text = claude.complete(modelPolicy.resolveModel(LlmIntent.FIX_DSL), system, user);
        return mapper.readValue(claude.extractJson(text), RuleDSL.class);
    }

    /**
     * Self-healing loop — validate, and while invalid ask the LLM to repair,
     * up to {@value #MAX_HEAL_ATTEMPTS} attempts.
     */
    public HealResult selfHealDsl(RuleDSL initial) throws Exception {
        HealResult result = new HealResult();
        RuleDSL current = initial;
        for (int attempt = 1; attempt <= MAX_HEAL_ATTEMPTS; attempt++) {
            DslValidator.Result v = dslValidator.validate(current);
            if (v.ok()) {
                result.dsl = current;
                result.healed = true;
                result.attempts = attempt - 1;
                metrics.recordRetryOutcome(true);
                return result;
            }
            List<String> errs = new ArrayList<>();
            for (DslValidator.Issue i : v.errors) errs.add("[" + i.code + "] " + i.message);
            result.attemptLog.add("attempt " + attempt + " — errors: " + errs);
            metrics.recordRetryAttempt(attempt);
            current = repairDsl(mapper.writeValueAsString(current), errs);
            result.attempts = attempt;
        }
        DslValidator.Result finalCheck = dslValidator.validate(current);
        result.dsl = current;
        result.healed = finalCheck.ok();
        metrics.recordRetryOutcome(result.healed);
        for (DslValidator.Issue i : finalCheck.errors) {
            result.remainingErrors.add("[" + i.code + "] " + i.message);
        }
        return result;
    }

    // ─────────────────────────── explain_decision ───────────────────────────

    /** Post-execution natural-language "why" narrative from an execution trace. */
    public String explainDecision(IrExecutor.ExecutionResult exec) throws Exception {
        String system = "You explain a rule-execution outcome to a business reader in 2-3 "
                + "sentences. Be precise and neutral. Output prose only.";
        StringBuilder u = new StringBuilder();
        u.append("Rule: ").append(exec.ruleId).append("\n");
        u.append("Conditions met: ").append(exec.conditionsMet).append("\n");
        u.append("Branch taken: ").append(exec.branchTaken).append("\n");
        u.append("Passed: ").append(exec.passed).append("\n");
        u.append("Trace:\n");
        for (String t : exec.trace) u.append("  ").append(t).append("\n");
        metrics.recordLlmCall(LlmIntent.EXPLAIN_DECISION.key());
        return claude.complete(modelPolicy.resolveModel(LlmIntent.EXPLAIN_DECISION),
                system, u.toString()).trim();
    }

    /** Result of a {@link #selfHealDsl} run. */
    public static class HealResult {
        public RuleDSL dsl;
        public boolean healed;
        public int attempts;
        public List<String> attemptLog = new ArrayList<>();
        public List<String> remainingErrors = new ArrayList<>();
    }
}
