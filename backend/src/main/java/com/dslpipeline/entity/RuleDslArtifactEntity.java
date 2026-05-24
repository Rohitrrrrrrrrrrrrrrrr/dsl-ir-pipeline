package com.dslpipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * rule_dsl_artifact — the versioned, human-authored DSL form of a rule.
 * Child of {@link RuleDefinitionEntity}.
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "rule_dsl_artifact", indexes = @Index(name = "idx_dsl_rule", columnList = "rule_id"))
public class RuleDslArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dsl_id")
    private Long dslId;

    @Column(name = "dsl_uuid", nullable = false, length = 36)
    private String dslUuid = UUID.randomUUID().toString();

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    /** The RuleDSL JSON. */
    @Column(name = "dsl_spec", nullable = false, columnDefinition = "TEXT")
    private String dslSpec;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(nullable = false)
    private String author = "system";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getDslId() { return dslId; }
    public void setDslId(Long dslId) { this.dslId = dslId; }
    public String getDslUuid() { return dslUuid; }
    public void setDslUuid(String dslUuid) { this.dslUuid = dslUuid; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getDslSpec() { return dslSpec; }
    public void setDslSpec(String dslSpec) { this.dslSpec = dslSpec; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
