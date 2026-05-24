package com.dslpipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * core_tenant — the multi-tenant backbone (faithful to the ZenLogIQ schema).
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "core_tenant", uniqueConstraints = @UniqueConstraint(columnNames = "tenant_code"))
public class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "tenant_code", nullable = false, length = 32)
    private String tenantCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(length = 16)
    private String locale = "en-AU";

    @Column(name = "time_zone", length = 64)
    private String timeZone = "Australia/Sydney";

    @Column(length = 3)
    private String currency = "AUD";

    /** LLM config as JSON ({provider, model, maxTokens, ...}). */
    @Column(name = "llm_config", columnDefinition = "TEXT")
    private String llmConfig;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getLlmConfig() { return llmConfig; }
    public void setLlmConfig(String llmConfig) { this.llmConfig = llmConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
