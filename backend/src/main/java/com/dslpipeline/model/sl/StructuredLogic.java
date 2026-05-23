package com.dslpipeline.model.sl;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured Logic (SL) — the disambiguation layer.
 *
 * A cleaned-up, line-by-line restatement of the business rule. Still natural
 * language, but standardised and explicit. Annotated with confidence and
 * ambiguity flags so the LLM never silently resolves missing meaning — per the
 * principle "ambiguity is surfaced, not resolved silently".
 *
 * @author Nikunj Malik
 */
public class StructuredLogic {

    private String ruleId;
    private String title;
    private List<Clause> clauses = new ArrayList<>();

    /** Things the system explicitly assumed (visible, reviewable). */
    private List<String> assumptions = new ArrayList<>();

    /** Ambiguities that need author resolution before this rule can be trusted. */
    private List<String> ambiguities = new ArrayList<>();

    /** Free-form notes (unparsed sentences, etc.). */
    private List<String> notes = new ArrayList<>();

    /** Overall confidence 0..1 in this normalisation. */
    private double confidence = 1.0;

    /** Which strategy produced this SL: "rule" | "claude". */
    private String strategy = "rule";

    private String originalNl;

    public StructuredLogic() {}

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<Clause> getClauses() { return clauses; }
    public void setClauses(List<Clause> clauses) { this.clauses = clauses; }
    public List<String> getAssumptions() { return assumptions; }
    public void setAssumptions(List<String> assumptions) { this.assumptions = assumptions; }
    public List<String> getAmbiguities() { return ambiguities; }
    public void setAmbiguities(List<String> ambiguities) { this.ambiguities = ambiguities; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getOriginalNl() { return originalNl; }
    public void setOriginalNl(String originalNl) { this.originalNl = originalNl; }

    /** Render SL back to a human-readable block. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append("# ").append(title).append("\n\n");
        for (int i = 0; i < clauses.size(); i++) {
            Clause c = clauses.get(i);
            sb.append("Clause ").append(i + 1).append(":\n");
            sb.append("  IF ").append(c.getCondition()).append("\n");
            sb.append("  THEN ").append(c.getOutcome()).append("\n");
            if (c.getElseOutcome() != null && !c.getElseOutcome().isBlank()) {
                sb.append("  ELSE ").append(c.getElseOutcome()).append("\n");
            }
            sb.append("\n");
        }
        if (!assumptions.isEmpty()) {
            sb.append("Assumptions:\n");
            for (String a : assumptions) sb.append("  - ").append(a).append("\n");
        }
        if (!ambiguities.isEmpty()) {
            sb.append("Ambiguities (require author resolution):\n");
            for (String a : ambiguities) sb.append("  ! ").append(a).append("\n");
        }
        return sb.toString();
    }

    /** One IF/THEN/ELSE statement of logic. */
    public static class Clause {
        private String condition;     // e.g. "customer.age < 18"
        private String outcome;       // THEN branch, e.g. "decline the loan"
        private String elseOutcome;   // optional ELSE branch
        private String severity;      // "error" | "warning" | "ensure"
        private String code;          // outcome code, e.g. "LOAN_DECLINED"
        private String message;       // human message
        private String elseCode;      // ELSE outcome code
        private String elseMessage;   // ELSE human message
        private String elseSeverity;  // ELSE severity
        private double confidence = 1.0;

        public Clause() {}
        public Clause(String condition, String outcome) {
            this.condition = condition;
            this.outcome = outcome;
        }

        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
        public String getElseOutcome() { return elseOutcome; }
        public void setElseOutcome(String elseOutcome) { this.elseOutcome = elseOutcome; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getElseCode() { return elseCode; }
        public void setElseCode(String elseCode) { this.elseCode = elseCode; }
        public String getElseMessage() { return elseMessage; }
        public void setElseMessage(String elseMessage) { this.elseMessage = elseMessage; }
        public String getElseSeverity() { return elseSeverity; }
        public void setElseSeverity(String elseSeverity) { this.elseSeverity = elseSeverity; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }
}
