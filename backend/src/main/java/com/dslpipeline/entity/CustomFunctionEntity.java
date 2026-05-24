package com.dslpipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * custom_extension_function — project-level functions, stored as SpEL expressions.
 *
 * These are loaded by {@code CustomFunctionService} and registered into the
 * {@code ExtensionRegistry} at runtime — no deployment needed to add a function.
 *
 * @author Nikunj Malik
 */
@Entity
@Table(name = "custom_extension_function",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"tenant_id", "project_id", "namespace", "function_name"}))
public class CustomFunctionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "function_uuid", nullable = false, length = 36)
    private String functionUuid = UUID.randomUUID().toString();

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "project_id", length = 100)
    private String projectId;

    @Column(nullable = false, length = 100)
    private String namespace = "custom";

    @Column(name = "function_name", nullable = false, length = 200)
    private String functionName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** JSON array: [{ "name":"x", "type":"number" }, ...] */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String parameters = "[]";

    @Column(name = "return_type", nullable = false, length = 50)
    private String returnType = "boolean";

    /** SpEL expression. Parameter names are exposed as #variables. */
    @Column(name = "body_expression", nullable = false, columnDefinition = "TEXT")
    private String bodyExpression;

    /** draft | active | inactive */
    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "last_test_result", columnDefinition = "TEXT")
    private String lastTestResult;

    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Fully-namespaced callable name, e.g. {@code custom.riskScore}. */
    public String qualifiedName() {
        return namespace + "." + functionName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFunctionUuid() { return functionUuid; }
    public void setFunctionUuid(String functionUuid) { this.functionUuid = functionUuid; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getFunctionName() { return functionName; }
    public void setFunctionName(String functionName) { this.functionName = functionName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }
    public String getBodyExpression() { return bodyExpression; }
    public void setBodyExpression(String bodyExpression) { this.bodyExpression = bodyExpression; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastTestResult() { return lastTestResult; }
    public void setLastTestResult(String lastTestResult) { this.lastTestResult = lastTestResult; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
