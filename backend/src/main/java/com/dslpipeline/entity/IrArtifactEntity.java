package com.dslpipeline.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Versioned IR artifact — every successful end-to-end compile produces a row.
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "ir_artifact", indexes = {
        @Index(name = "idx_ir_rule_id", columnList = "ruleId"),
        @Index(name = "idx_ir_compiled_at", columnList = "compiledAt")
})
public class IrArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String ruleId;

    @Column(length = 32)
    private String version;

    @Column(nullable = false)
    private Instant compiledAt;

    @Lob
    @Column(name = "original_nl")
    private String originalNl;

    @Lob
    @Column(name = "sl_json")
    private String slJson;

    @Lob
    @Column(name = "dsl_json")
    private String dslJson;

    @Lob
    @Column(name = "ast_json")
    private String astJson;

    @Lob
    @Column(name = "ir_json")
    private String irJson;

    @Column(length = 64)
    private String dslHash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Instant getCompiledAt() { return compiledAt; }
    public void setCompiledAt(Instant compiledAt) { this.compiledAt = compiledAt; }
    public String getOriginalNl() { return originalNl; }
    public void setOriginalNl(String originalNl) { this.originalNl = originalNl; }
    public String getSlJson() { return slJson; }
    public void setSlJson(String slJson) { this.slJson = slJson; }
    public String getDslJson() { return dslJson; }
    public void setDslJson(String dslJson) { this.dslJson = dslJson; }
    public String getAstJson() { return astJson; }
    public void setAstJson(String astJson) { this.astJson = astJson; }
    public String getIrJson() { return irJson; }
    public void setIrJson(String irJson) { this.irJson = irJson; }
    public String getDslHash() { return dslHash; }
    public void setDslHash(String dslHash) { this.dslHash = dslHash; }
}
