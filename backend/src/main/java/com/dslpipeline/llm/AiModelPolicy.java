package com.dslpipeline.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-intent model routing — the faithful equivalent of the {@code ai_model_policy}
 * table. Resolution order: explicit per-intent override → system default.
 *
 * Overrides are held in memory and editable at runtime via the Admin API, so
 * QA can re-route an intent to a different model without redeployment.
 *
 * @author Nikunj Malik
 */
@Component
public class AiModelPolicy {

    @Value("${pipeline.llm.anthropic.model:claude-sonnet-4-5-20250929}")
    private String defaultModel;

    private final Map<LlmIntent, String> overrides = new EnumMap<>(LlmIntent.class);

    /** Resolve the model id for an intent. */
    public String resolveModel(LlmIntent intent) {
        return overrides.getOrDefault(intent, defaultModel);
    }

    /** Set a per-intent override (Admin API). */
    public void setOverride(LlmIntent intent, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            overrides.remove(intent);
        } else {
            overrides.put(intent, modelId);
        }
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    /** Current routing table (intent key → resolved model). */
    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (LlmIntent intent : LlmIntent.values()) {
            out.put(intent.key(), resolveModel(intent));
        }
        return out;
    }
}
