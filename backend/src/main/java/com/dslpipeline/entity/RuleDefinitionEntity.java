package com.dslpipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * rule_definition — the authored rule record (faithful to the ZenLogIQ schema).
 *
 * A rule is NOT stored as condition/action rows; its executable form lives in
 * the child {@code rule_dsl_artifact} and {@code rule_ir_artifact} tables. This
 * row holds identity, business intent and structured content.
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "rule_definition",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"tenant_code", "project_key", "namespace", "rule_key"}))
public class RuleDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_uuid", nullable = false, length = 36)
    private String ruleUuid = UUID.randomUUID().toString();

    @Column(name = "tenant_code", nullable = false, length = 32)
    private String tenantCode;

    @Column(name = "project_key", nullable = false, length = 48)
    private String projectKey;

    // ── 1. Identity ──
    @Column(nullable = false, length = 64)
    private String namespace = "default";

    @Column(name = "rule_key", nullable = false, length = 128)
    private String ruleKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    /** draft | ready | active | suspended | deprecated */
    @Column(nullable = false, length = 20)
    private String status = "draft";

    /** deterministic | llm | hybrid | decisionTable | decisionTree */
    @Column(nullable = false, length = 32)
    private String type = "deterministic";

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(name = "source_ref", columnDefinition = "TEXT")
    private String sourceRef;

    // ── 2. Business Intent ──
    @Column(columnDefinition = "TEXT")
    private String intent;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(length = 16)
    private String materiality;

    // ── 3. Structured Content (JSON text) ──
    @Column(name = "conditions_json", columnDefinition = "TEXT")
    private String conditionsJson;

    @Column(name = "outcomes_json", columnDefinition = "TEXT")
    private String outcomesJson;

    private Integer priority;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    // ── 8. System & Audit ──
    @Column(name = "rule_version", nullable = false, length = 32)
    private String ruleVersion = "1.0.0";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false)
    private String updatedBy = "system";

    @Column(nullable = false)
    private Integer version = 1;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleUuid() { return ruleUuid; }
    public void setRuleUuid(String ruleUuid) { this.ruleUuid = ruleUuid; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getMateriality() { return materiality; }
    public void setMateriality(String materiality) { this.materiality = materiality; }
    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }
    public String getOutcomesJson() { return outcomesJson; }
    public void setOutcomesJson(String outcomesJson) { this.outcomesJson = outcomesJson; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Instant effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
