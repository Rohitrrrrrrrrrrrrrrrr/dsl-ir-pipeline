package com.dslpipeline.model.dsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level RuleDSL container — the deterministic, human-readable canonical
 * authoring representation that gets compiled into IR.
 *
 * Conditions describe WHEN the rule matches. When they pass, {@code actions}
 * (the THEN branch) run; when they fail, {@code elseActions} (the optional ELSE
 * branch) run. This supports both authoring styles:
 *   - "customer.age &lt; 18 → addError"          (THEN-only, violation style)
 *   - "age &gt;= 18 → ENSURE ELIGIBLE ELSE RAISE" (THEN/ELSE, eligibility style)
 *
 * @author Nikunj Malik
 */
public class RuleDSL {

    private String ruleId;
    private int priority = 100;
    private List<ConditionNode> conditions = new ArrayList<>();
    private List<DslAction> actions = new ArrayList<>();
    private List<DslAction> elseActions = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();
    private boolean haltOnViolation = false;
    private String roundingMode = "HALF_UP";

    /** Effective-dating window (ISO date strings), optional. */
    private String effectiveFrom;
    private String effectiveTo;

    public RuleDSL() {}

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public List<ConditionNode> getConditions() { return conditions; }
    public void setConditions(List<ConditionNode> conditions) { this.conditions = conditions; }

    public List<DslAction> getActions() { return actions; }
    public void setActions(List<DslAction> actions) { this.actions = actions; }

    public List<DslAction> getElseActions() { return elseActions; }
    public void setElseActions(List<DslAction> elseActions) { this.elseActions = elseActions; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public boolean isHaltOnViolation() { return haltOnViolation; }
    public void setHaltOnViolation(boolean haltOnViolation) { this.haltOnViolation = haltOnViolation; }

    public String getRoundingMode() { return roundingMode; }
    public void setRoundingMode(String roundingMode) { this.roundingMode = roundingMode; }

    public String getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public String getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
}
