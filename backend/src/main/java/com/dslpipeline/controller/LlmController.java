package com.dslpipeline.controller;

import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.llm.AiModelPolicy;
import com.dslpipeline.llm.LlmIntent;
import com.dslpipeline.llm.LlmService;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.sl.StructuredLogic;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for the LLM assist layer — normalize / translate / narrate / fix /
 * explain, plus per-intent model routing.
 *
 * The LLM only ever TRANSLATES or EXPLAINS; it never executes rules.
 *
 * @author Nikunj Malik
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final LlmService llm;
    private final AiModelPolicy modelPolicy;
    private final ObjectMapper mapper;

    public LlmController(LlmService llm, AiModelPolicy modelPolicy, ObjectMapper mapper) {
        this.llm = llm;
        this.modelPolicy = modelPolicy;
        this.mapper = mapper;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("available", llm.isAvailable(),
                "note", llm.isAvailable() ? "Claude configured"
                        : "Set ANTHROPIC_API_KEY to enable LLM features");
    }

    @GetMapping("/model-policy")
    public Map<String, String> modelPolicy() {
        return modelPolicy.snapshot();
    }

    @PutMapping("/model-policy/{intent}")
    public Map<String, String> setModel(@PathVariable String intent,
                                        @RequestBody Map<String, String> body) {
        modelPolicy.setOverride(LlmIntent.fromKey(intent), body.get("model"));
        return modelPolicy.snapshot();
    }

    @PostMapping("/normalize")
    public StructuredLogic normalize(@RequestBody Map<String, Object> body) throws Exception {
        return llm.normalizeNl((String) body.get("nl"));
    }

    @PostMapping("/translate")
    public RuleDSL translate(@RequestBody Map<String, Object> body) throws Exception {
        String sl = String.valueOf(body.get("structuredLogic"));
        String schema = body.get("schema") == null ? "{}"
                : mapper.writeValueAsString(body.get("schema"));
        return llm.translateToDsl(sl, schema);
    }

    @PostMapping("/narrate")
    public Map<String, Object> narrate(@RequestBody Map<String, Object> body) throws Exception {
        String dslJson = mapper.writeValueAsString(body.get("dsl"));
        return Map.of("narration", llm.narrate(dslJson));
    }

    @PostMapping("/fix")
    public LlmService.HealResult fix(@RequestBody Map<String, Object> body) throws Exception {
        RuleDSL dsl = mapper.convertValue(body.get("dsl"), RuleDSL.class);
        return llm.selfHealDsl(dsl);
    }

    @PostMapping("/explain")
    public Map<String, Object> explain(@RequestBody Map<String, Object> body) throws Exception {
        IrExecutor.ExecutionResult exec =
                mapper.convertValue(body.get("execution"), IrExecutor.ExecutionResult.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("explanation", llm.explainDecision(exec));
        return out;
    }
}
