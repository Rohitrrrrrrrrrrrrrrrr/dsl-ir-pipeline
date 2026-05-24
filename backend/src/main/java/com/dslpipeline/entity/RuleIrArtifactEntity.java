package com.dslpipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * rule_ir_artifact — the canonical, compiled IR form of a rule.
 * Child of {@link RuleDefinitionEntity}, sourced from a {@link RuleDslArtifactEntity}.
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "rule_ir_artifact", indexes = @Index(name = "idx_ir_rule", columnList = "rule_id"))
public class RuleIrArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ir_id")
    private Long irId;

    @Column(name = "ir_uuid", nullable = false, length = 36)
    private String irUuid = UUID.randomUUID().toString();

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "source_dsl_id", nullable = false)
    private Long sourceDslId;

    /** The CanonicalIR JSON. */
    @Column(name = "ir_schema", nullable = false, columnDefinition = "TEXT")
    private String irSchema;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "numeric_sem", nullable = false, length = 64)
    private String numericSem = "AMOUNT_8_RATE_12";

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Australia/Sydney";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getIrId() { return irId; }
    public void setIrId(Long irId) { this.irId = irId; }
    public String getIrUuid() { return irUuid; }
    public void setIrUuid(String irUuid) { this.irUuid = irUuid; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getSourceDslId() { return sourceDslId; }
    public void setSourceDslId(Long sourceDslId) { this.sourceDslId = sourceDslId; }
    public String getIrSchema() { return irSchema; }
    public void setIrSchema(String irSchema) { this.irSchema = irSchema; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public String getNumericSem() { return numericSem; }
    public void setNumericSem(String numericSem) { this.numericSem = numericSem; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
