package com.dslpipeline.pipeline;

import com.dslpipeline.llm.ClaudeClient;
import com.dslpipeline.model.sl.StructuredLogic;
import com.dslpipeline.vocabulary.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 1 — NL → Structured Logic (the disambiguation layer).
 *
 * Strategies:
 *   - "rule"   — deterministic rule-based parser (default, no API key).
 *   - "claude" — Anthropic Claude API (requires ANTHROPIC_API_KEY); falls back
 *                to rule-based on any failure.
 *
 * Vague language is surfaced as an ambiguity, never silently resolved.
 *
 * @author Nikunj Malik
 */
@Component
public class NlToSlConverter {

    private static final Logger log = LoggerFactory.getLogger(NlToSlConverter.class);

    private final ClaudeClient claudeClient;
    private final Vocabulary vocabulary;

    public NlToSlConverter(ClaudeClient claudeClient, Vocabulary vocabulary) {
        this.claudeClient = claudeClient;
        this.vocabulary = vocabulary;
    }

    /** Vague quantifiers that need an explicit threshold before the rule is trustworthy. */
    private static final List<String> VAGUE_TERMS = List.of(
            "high", "low", "fair", "reasonable", "significant", "substantial",
            "large", "small", "soon", "recently", "a lot", "many", "appropriate", "adequate");

    public StructuredLogic convert(String nl, String strategy) {
        if ("claude".equalsIgnoreCase(strategy) && claudeClient.isAvailable()) {
            try {
                StructuredLogic sl = claudeClient.normalizeToSl(nl);
                sl.setStrategy("claude");
                return sl;
            } catch (Exception e) {
                log.warn("Claude SL conversion failed, falling back to rule-based: {}", e.getMessage());
            }
        }
        StructuredLogic sl = ruleBasedConvert(nl);
        sl.setStrategy("rule");
        return sl;
    }

    // ─────────────────────────── patterns ───────────────────────────

    private static final String OPS = "<=|>=|==|!=|<|>";
    private static final String VERB = "decline|reject|deny|approve|accept|allow|warn|flag|review|hold|ensure|set|require";

    // "<subject> <op> <value> <verb> <object>"
    private static final Pattern INLINE = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z0-9_\\s.]*?)\\s*(" + OPS + ")\\s*" +
                    "(\"[^\"]*\"|'[^']*'|[\\w$,.\\-]+)\\s+(" + VERB + ")\\b\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    // "<subject> must|should be <op> <value>" — a requirement constraint
    private static final Pattern MUST_BE = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z0-9_\\s.]*?)\\s+(?:must|should|needs? to)\\s+(?:be\\s+)?" +
                    "(" + OPS + ")\\s*(\"[^\"]*\"|'[^']*'|[\\w$,.\\-]+).*$",
            Pattern.CASE_INSENSITIVE);

    // "if <cond> then <action> [else <action>]"
    private static final Pattern IF_THEN = Pattern.compile(
            "^\\s*if\\s+(.+?)\\s+then\\s+(.+?)(?:\\s+else\\s+(.+?))?\\s*[.]?\\s*$",
            Pattern.CASE_INSENSITIVE);

    // "when <cond> [,] <verb> <object>"
    private static final Pattern WHEN = Pattern.compile(
            "^\\s*when\\s+(.+?)[,]?\\s+(" + VERB + ")\\b\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    // "<cond> is/are [not] eligible [unless <exception>]"
    private static final Pattern ELIGIBILITY = Pattern.compile(
            "^\\s*(.+?)\\s+(?:is|are)\\s+(not\\s+)?eligible\\b\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final java.util.Set<String> PERSON_ENTITIES = java.util.Set.of(
            "customer", "customers", "applicant", "applicants", "member", "members", "person");

    // ─────────────────────────── rule-based conversion ───────────────────────────

    private StructuredLogic ruleBasedConvert(String nl) {
        StructuredLogic sl = new StructuredLogic();
        sl.setRuleId("rule_" + UUID.randomUUID().toString().substring(0, 8));
        sl.setOriginalNl(nl);
        sl.setTitle(deriveTitle(nl));

        String normalized = nl == null ? "" : nl.trim().replaceAll("\\s+", " ");
        String[] sentences = normalized.split("(?<=[.!])\\s+");

        List<StructuredLogic.Clause> clauses = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> ambiguities = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();

        double confidenceAcc = 0.0;
        int scored = 0;

        for (String raw : sentences) {
            String sentence = raw.trim();
            if (sentence.isEmpty()) continue;

            // vocabulary normalisation (synonyms → canonical operators/terms)
            String canon = vocabulary.normaliseSentence(sentence);

            detectAmbiguity(sentence, ambiguities);

            StructuredLogic.Clause clause = parseClause(canon, assumptions);
            if (clause != null) {
                clauses.add(clause);
                confidenceAcc += clause.getConfidence();
                scored++;
            } else {
                notes.add("Unparsed sentence (could not be reduced to IF/THEN): \"" + sentence + "\"");
                ambiguities.add("Sentence not understood by the deterministic parser: \"" + sentence
                        + "\" — rephrase as 'if <condition> then <action>' or use the Claude strategy.");
            }
        }

        if (clauses.isEmpty()) {
            StructuredLogic.Clause c = new StructuredLogic.Clause();
            c.setCondition("(unparsed) " + normalized);
            c.setOutcome("(unparsed)");
            c.setSeverity("error");
            c.setCode("UNPARSED_RULE");
            c.setMessage(normalized);
            c.setConfidence(0.0);
            clauses.add(c);
        }

        sl.setClauses(clauses);
        sl.setNotes(notes);
        sl.setAmbiguities(ambiguities);
        sl.setAssumptions(assumptions);

        double base = scored > 0 ? confidenceAcc / scored : 0.0;
        // each open ambiguity reduces confidence
        double penalty = Math.min(0.5, ambiguities.size() * 0.15);
        sl.setConfidence(Math.max(0.0, round2(base - penalty)));
        return sl;
    }

    private void detectAmbiguity(String sentence, List<String> ambiguities) {
        String lower = sentence.toLowerCase();
        boolean hasNumber = Pattern.compile("\\d").matcher(lower).find();
        for (String vague : VAGUE_TERMS) {
            if (Pattern.compile("\\b" + Pattern.quote(vague) + "\\b").matcher(lower).find() && !hasNumber) {
                ambiguities.add("Vague term \"" + vague + "\" has no explicit threshold — "
                        + "please specify a concrete value/unit.");
            }
        }
        if (lower.contains("$") || lower.contains(" aud") || lower.contains(" usd")) {
            // currency present — fine
        } else if (Pattern.compile("\\b(amount|income|total|price|salary|balance)\\b")
                .matcher(lower).find() && hasNumber) {
            ambiguities.add("Monetary value has no currency specified — assuming a unit-less number.");
        }
    }

    private StructuredLogic.Clause parseClause(String sentence, List<String> assumptions) {
        Matcher m;

        // if <cond> then <action> [else <action>]
        m = IF_THEN.matcher(sentence);
        if (m.find()) {
            String cond = canonicaliseCondition(m.group(1), assumptions);
            if (cond == null) return null;
            StructuredLogic.Clause c = buildOutcomeClause(cond, m.group(2));
            if (m.group(3) != null && !m.group(3).isBlank()) {
                applyElse(c, m.group(3));
            }
            return c;
        }

        // when <cond> [,] <verb> <object>
        m = WHEN.matcher(sentence);
        if (m.find()) {
            String cond = canonicaliseCondition(m.group(1), assumptions);
            if (cond == null) return null;
            return buildOutcomeClause(cond, m.group(2) + " " + m.group(3));
        }

        // <cond> is/are [not] eligible [unless <exception>]
        m = ELIGIBILITY.matcher(sentence);
        if (m.find()) {
            boolean negated = m.group(2) != null;
            String rest = m.group(3) == null ? "" : m.group(3).trim();
            String condRaw = m.group(1) + (rest.toLowerCase().startsWith("unless") ? " " + rest : "");
            String cond = canonicaliseCondition(condRaw, assumptions);
            if (cond == null) return null;
            // "not eligible" → the condition firing means an INELIGIBLE error
            return negated
                    ? buildOutcomeClause(cond, "decline ineligible applicant")
                    : buildOutcomeClause(cond, "ensure result.status = ELIGIBLE");
        }

        // <subject> <op> <value> <verb> <object>
        m = INLINE.matcher(sentence);
        if (m.find()) {
            String cond = canonicaliseFragment(m.group(1), m.group(2), m.group(3), assumptions);
            if (cond == null) return null;
            return buildOutcomeClause(cond, m.group(4) + " " + m.group(5));
        }

        // <subject> must be <op> <value>  → a requirement (error fires on ELSE branch)
        m = MUST_BE.matcher(sentence);
        if (m.find()) {
            String cond = canonicaliseFragment(m.group(1), m.group(2), m.group(3), assumptions);
            if (cond == null) return null;
            return buildRequirementClause(cond);
        }
        return null;
    }

    /** Canonicalise a (possibly multi-clause) condition string into "path op value [AND/OR ...]". */
    private String canonicaliseCondition(String raw, List<String> assumptions) {
        String unlessNote = null;
        String body = raw;
        Matcher unless = Pattern.compile("(.+?)\\s+unless\\s+(.+)", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (unless.find()) {
            body = unless.group(1);
            unlessNote = unless.group(2).trim();
        }

        // split on AND / OR keywords (uppercase), keep the connectors
        String[] parts = body.split("(?i)\\s+(and|or)\\s+");
        String[] connectors = collectConnectors(body);

        List<String> canonParts = new ArrayList<>();
        for (String part : parts) {
            String c = canonicaliseFreeFragment(part.trim(), assumptions);
            if (c == null) return null;
            canonParts.add(c);
        }
        StringBuilder sb = new StringBuilder(canonParts.get(0));
        for (int i = 1; i < canonParts.size(); i++) {
            sb.append(' ').append(i - 1 < connectors.length ? connectors[i - 1] : "AND")
              .append(' ').append(canonParts.get(i));
        }

        if (unlessNote != null) {
            String exception = canonicaliseException(unlessNote);
            assumptions.add("'unless " + unlessNote + "' interpreted as the exception condition "
                    + exception + " — verify this mapping.");
            sb.insert(0, "(").append(") AND NOT (").append(exception).append(")");
        }
        return sb.toString();
    }

    private String[] collectConnectors(String body) {
        Matcher m = Pattern.compile("(?i)\\s+(and|or)\\s+").matcher(body);
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group(1).toUpperCase());
        return out.toArray(new String[0]);
    }

    /** Canonicalise a free-form fragment like "customer age < 18" or "income > 50,000 AUD". */
    private String canonicaliseFreeFragment(String fragment, List<String> assumptions) {
        Matcher m = Pattern.compile(
                "^\\s*([A-Za-z][A-Za-z0-9_\\s.]*?(?:\\([^)]*\\))?)\\s*(" + OPS + ")\\s*" +
                        "(\"[^\"]*\"|'[^']*'|[\\w$,.\\-]+)\\s*(.*)$",
                Pattern.CASE_INSENSITIVE).matcher(fragment);
        if (!m.find()) return null;
        return canonicaliseFragment(m.group(1), m.group(2), m.group(3), assumptions);
    }

    /** Build "path op value" from raw subject/op/value tokens. */
    private String canonicaliseFragment(String subject, String op, String value, List<String> assumptions) {
        String path = canonicalSubject(subject);
        String canonValue = canonicalValue(value, assumptions);
        if (path == null || canonValue == null) return null;

        // heuristic: "Customers < 18" — a bare person entity compared numerically
        // means age. Documented mapping; recorded as a visible assumption.
        boolean numeric = canonValue.matches("[-+]?\\d+(?:\\.\\d+)?");
        boolean ordered = op.trim().equals("<") || op.trim().equals("<=")
                || op.trim().equals(">") || op.trim().equals(">=");
        if (numeric && ordered && !path.contains(".") && PERSON_ENTITIES.contains(path)) {
            String singular = path.endsWith("s") ? path.substring(0, path.length() - 1) : path;
            String agePath = singular + ".age";
            assumptions.add("Interpreted '" + subject.trim() + " " + op.trim() + " " + value
                    + "' as " + agePath + " " + op.trim() + " " + canonValue
                    + " (a person compared to a number means age) — verify this mapping.");
            return agePath + " " + op.trim() + " " + canonValue;
        }
        return path + " " + op.trim() + " " + canonValue;
    }

    private String canonicalSubject(String subject) {
        String s = subject.trim();
        // a function call passes through untouched
        if (s.matches(".*\\([^)]*\\).*")) return s.replaceAll("\\s+", "");
        // apply vocabulary alias to the leading noun
        String[] words = s.split("\\s+");
        if (words.length > 0) {
            words[0] = vocabulary.resolveAlias(words[0]);
        }
        return String.join(".", words).toLowerCase();
    }

    private String canonicalValue(String value, List<String> assumptions) {
        String v = value.trim();
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v;
        }
        // currency / thousands separators
        String cleaned = v.replace("$", "").replace(",", "")
                .replaceAll("(?i)\\b(aud|usd|nzd|gbp|eur)\\b", "").trim();
        if (cleaned.matches("[-+]?\\d+")) return cleaned;
        if (cleaned.matches("[-+]?\\d+\\.\\d+")) return cleaned;
        if (cleaned.equalsIgnoreCase("true") || cleaned.equalsIgnoreCase("false")) {
            return cleaned.toLowerCase();
        }
        if (cleaned.isEmpty()) return null;
        return "\"" + cleaned + "\"";
    }

    private String canonicaliseException(String raw) {
        String lower = raw.toLowerCase();
        if (lower.contains("approval") || lower.contains("approved") || lower.contains("override")) {
            return "override.approved == true";
        }
        return "override.reason != \"\"";
    }

    private StructuredLogic.Clause buildOutcomeClause(String cond, String action) {
        StructuredLogic.Clause c = new StructuredLogic.Clause();
        c.setCondition(cond);
        c.setOutcome(action.trim());
        String lower = action.toLowerCase().trim();
        if (lower.startsWith("warn") || lower.startsWith("flag") || lower.startsWith("review")) {
            c.setSeverity("warning");
        } else if (lower.startsWith("approve") || lower.startsWith("accept") || lower.startsWith("allow")
                || lower.startsWith("ensure") || lower.startsWith("set")) {
            c.setSeverity("ensure");
        } else {
            c.setSeverity("error");
        }
        c.setCode(deriveCode(c.getSeverity(), action));
        c.setMessage(humanMessage(cond, action));
        c.setConfidence(0.9);
        return c;
    }

    private void applyElse(StructuredLogic.Clause c, String elseAction) {
        c.setElseOutcome(elseAction.trim());
        String lower = elseAction.toLowerCase().trim();
        if (lower.startsWith("warn") || lower.startsWith("flag")) c.setElseSeverity("warning");
        else if (lower.startsWith("approve") || lower.startsWith("accept") || lower.startsWith("ensure")
                || lower.startsWith("set") || lower.startsWith("allow")) c.setElseSeverity("ensure");
        else c.setElseSeverity("error");
        c.setElseCode(deriveCode(c.getElseSeverity(), elseAction));
        c.setElseMessage(elseAction.trim());
    }

    private StructuredLogic.Clause buildRequirementClause(String cond) {
        // A requirement: the condition is the REQUIRED state.
        // If the requirement is NOT met, an error is raised (ELSE branch).
        StructuredLogic.Clause c = new StructuredLogic.Clause();
        c.setCondition(cond);
        c.setOutcome("requirement satisfied");
        c.setSeverity("ensure");
        c.setCode("REQUIREMENT_MET");
        c.setMessage("Requirement satisfied: " + cond);
        c.setElseOutcome("raise requirement violation");
        c.setElseSeverity("error");
        c.setElseCode("REQUIREMENT_NOT_MET");
        c.setElseMessage("Requirement not met: " + cond);
        c.setConfidence(0.85);
        return c;
    }

    private String deriveCode(String severity, String text) {
        String lower = text.toLowerCase();
        String domain =
                lower.contains("loan") ? "LOAN" :
                lower.contains("claim") ? "CLAIM" :
                lower.contains("policy") ? "POLICY" :
                lower.contains("order") ? "ORDER" : "RULE";
        return switch (severity) {
            case "warning" -> domain + "_FLAGGED";
            case "ensure" -> domain + "_OK";
            default -> domain + "_DECLINED";
        };
    }

    private String humanMessage(String cond, String action) {
        String a = action.trim();
        return Character.toUpperCase(a.charAt(0)) + a.substring(1) + " because " + cond;
    }

    private String deriveTitle(String nl) {
        String t = nl == null ? "" : nl.trim().replaceAll("\\s+", " ");
        return t.length() > 80 ? t.substring(0, 77) + "..." : t;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
