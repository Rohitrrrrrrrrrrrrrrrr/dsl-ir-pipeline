package com.dslpipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * config_databag — the declared schema for runtime variables.
 *
 * A databag is NOT runtime state; it declares the {@code fields[]} (path, type,
 * defaultValue) that seed {@code payload["dataBag"]} at execution start.
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "config_databag",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "project_key", "name"}))
public class ConfigDatabagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "databag_uuid", nullable = false, length = 36)
    private String databagUuid = UUID.randomUUID().toString();

    @Column(name = "tenant_code", nullable = false, length = 50)
    private String tenantCode;

    @Column(name = "project_key", nullable = false, length = 100)
    private String projectKey;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "active";

    /** JSON array: [{ "path":"discount", "type":"decimal", "defaultValue":0 }, ...] */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String fields = "[]";

    @Column(name = "created_by")
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_by")
    private String updatedBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDatabagUuid() { return databagUuid; }
    public void setDatabagUuid(String databagUuid) { this.databagUuid = databagUuid; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFields() { return fields; }
    public void setFields(String fields) { this.fields = fields; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
