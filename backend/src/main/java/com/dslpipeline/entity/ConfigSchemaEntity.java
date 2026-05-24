package com.dslpipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * config_schema — the domain schema (DAL contract) for a tenant/project.
 * Carries a status lifecycle: DRAFT → UNDER_REVIEW → APPROVED → ACTIVE → DEPRECATED.
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "config_schema",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"tenant_code", "project_key", "version_tag"}))
public class ConfigSchemaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schema_uuid", nullable = false, length = 36)
    private String schemaUuid = UUID.randomUUID().toString();

    @Column(name = "tenant_code", nullable = false, length = 100)
    private String tenantCode;

    @Column(name = "project_key", nullable = false, length = 100)
    private String projectKey;

    @Column(name = "version_tag", nullable = false, length = 20)
    private String versionTag = "v1";

    /** DRAFT | UNDER_REVIEW | APPROVED | ACTIVE | DEPRECATED | DELETED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    /** JSON object: { "customer.age": "number", "claim.amount": "decimal", ... } */
    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    private String schemaJson = "{}";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSchemaUuid() { return schemaUuid; }
    public void setSchemaUuid(String schemaUuid) { this.schemaUuid = schemaUuid; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
