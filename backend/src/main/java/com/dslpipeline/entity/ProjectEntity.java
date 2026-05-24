package com.dslpipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * core_project — projects hang off a tenant; everything else hangs off a project.
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "core_project",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "project_key"}))
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "tenant_code", nullable = false, length = 32)
    private String tenantCode;

    @Column(name = "project_key", nullable = false, length = 48)
    private String projectKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(name = "default_environment", length = 32)
    private String defaultEnvironment = "uat";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDefaultEnvironment() { return defaultEnvironment; }
    public void setDefaultEnvironment(String defaultEnvironment) { this.defaultEnvironment = defaultEnvironment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
