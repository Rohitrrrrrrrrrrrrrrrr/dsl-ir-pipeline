package com.dslpipeline.llm;

import com.dslpipeline.model.sl.StructuredLogic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anthropic Claude client for the NL → SL stage.
 *
 * The LLM only ever TRANSLATES — it produces a structured candidate with
 * explicit assumptions, ambiguities and a confidence score. It never decides;
 * the deterministic pipeline downstream is the gatekeeper.
 *
 * Active only when ANTHROPIC_API_KEY is configured.
 *
 * @author Nikunj Malik
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    @Value("${pipeline.llm.anthropic.api-key:}")
    private String apiKey;

    @Value("${pipeline.llm.anthropic.model:claude-sonnet-4-5-20250929}")
    private String model;

    @Value("${pipeline.llm.anthropic.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Value("${pipeline.llm.anthropic.version:2023-06-01}")
    private String anthropicVersion;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Most recent prompt hash — recorded into IR provenance. */
    private String lastPromptHash;
    public String getLastPromptHash() { return lastPromptHash; }

    public StructuredLogic normalizeToSl(String nl) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY not configured.");
        }
        String prompt = buildPrompt(nl);
        this.lastPromptHash = sha256(prompt);

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 1536,
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", anthropicVersion)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("Anthropic API error " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode root = mapper.readTree(resp.body());
        String text = root.path("content").path(0).path("text").asText();
        StructuredLogic sl = mapper.readValue(extractJson(text), StructuredLogic.class);
        if (sl.getRuleId() == null) {
            sl.setRuleId("rule_" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (sl.getClauses() == null) sl.setClauses(new ArrayList<>());
        sl.setOriginalNl(nl);
        return sl;
    }

    private String buildPrompt(String nl) {
        return """
            You translate Natural Language business rules into precise Structured Logic.
            You TRANSLATE only — never invent thresholds, never silently resolve ambiguity.
            If something is vague (e.g. "high income"), record it under "ambiguities".

            Output ONLY a JSON object of exactly this shape:
            {
              "ruleId": "rule_<short>",
              "title": "<short title>",
              "clauses": [
                {
                  "condition": "<path> <op> <value> [AND/OR <path> <op> <value>]",
                  "outcome": "<verb> <object>",
                  "elseOutcome": "<optional else action>",
                  "severity": "error | warning | ensure",
                  "code": "UPPER_SNAKE_CODE",
                  "message": "<human-readable message>",
                  "confidence": 0.0-1.0
                }
              ],
              "assumptions": ["<assumptions you had to make, visible to the author>"],
              "ambiguities": ["<unresolved ambiguities needing author input>"],
              "confidence": 0.0-1.0
            }

            Rules for the "condition" field:
              - use dot.notation paths (e.g. customer.age)
              - operators: < <= > >= == != , and 'in' / 'not in' with [a, b] lists
              - join multiple comparisons with uppercase AND / OR
              - date functions allowed: calculateAge(birthDate, refDate), compareDates(a, b),
                diffDays(a, b), isBetween(x, start, end, true)

            Natural-language rule:
            """ + nl;
    }

    private String extractJson(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("LLM did not return JSON: {}", s);
            throw new RuntimeException("Could not extract JSON from the LLM response.");
        }
        return s.substring(start, end + 1);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (Exception e) {
            return "n/a";
        }
    }
}
