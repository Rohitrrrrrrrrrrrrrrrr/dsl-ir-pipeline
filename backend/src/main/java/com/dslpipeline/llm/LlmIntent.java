package com.dslpipeline.llm;

/**
 * The LLM pipeline intents — each maps to its own model via {@link AiModelPolicy}.
 *
 * Mirrors the ZenLogIQ per-intent model routing: different pipeline stages can
 * be served by different models without redeployment. The LLM only ever
 * TRANSLATES / ASSISTS — it never executes rules or makes the decision.
 *
 * @author Nikunj Malik
 */
public enum LlmIntent {

    /** NL business text → Structured Logic. */
    NORMALIZE_NL("normalize_nl"),

    /** Structured Logic → DSL JSON. */
    TRANSLATE_DSL("translate_dsl"),

    /** Structured Logic / DSL → plain-English narration. */
    STRUCTURED_TO_NL("structured_to_nl"),

    /** Self-healing repair of an invalid DSL (retry loop). */
    FIX_DSL("fix_dsl"),

    /** Post-execution "why" explanation from a trace. */
    EXPLAIN_DECISION("explain_decision");

    private final String key;

    LlmIntent(String key) { this.key = key; }

    public String key() { return key; }

    public static LlmIntent fromKey(String key) {
        for (LlmIntent i : values()) {
            if (i.key.equalsIgnoreCase(key) || i.name().equalsIgnoreCase(key)) return i;
        }
        throw new IllegalArgumentException("Unknown LLM intent: " + key);
    }
}
