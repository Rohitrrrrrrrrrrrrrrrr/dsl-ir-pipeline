package com.dslpipeline.service;

import com.dslpipeline.entity.ProjectEntity;
import com.dslpipeline.entity.TenantEntity;
import com.dslpipeline.repository.ProjectRepository;
import com.dslpipeline.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CRUD for the multi-tenant backbone — {@code core_tenant} and {@code core_project}.
 * Everything else (rules, schemas, databags, functions) hangs off a project.
 *
 * @author Nikunj Malik
 */
@Service
public class TenantProjectService {

    private final TenantRepository tenantRepo;
    private final ProjectRepository projectRepo;

    public TenantProjectService(TenantRepository tenantRepo, ProjectRepository projectRepo) {
        this.tenantRepo = tenantRepo;
        this.projectRepo = projectRepo;
    }

    // ── tenants ──

    public List<TenantEntity> listTenants() {
        return tenantRepo.findAll();
    }

    public TenantEntity getTenant(String tenantCode) {
        return tenantRepo.findByTenantCode(tenantCode).orElseThrow(
                () -> new IllegalArgumentException("Tenant not found: " + tenantCode));
    }

    public TenantEntity createTenant(TenantEntity t) {
        if (t.getTenantCode() == null || t.getTenantCode().isBlank()) {
            throw new IllegalArgumentException("tenantCode is required.");
        }
        if (tenantRepo.existsByTenantCode(t.getTenantCode())) {
            throw new IllegalArgumentException("Tenant already exists: " + t.getTenantCode());
        }
        return tenantRepo.save(t);
    }

    // ── projects ──

    public List<ProjectEntity> listProjects(String tenantCode) {
        return projectRepo.findByTenantCode(tenantCode);
    }

    public ProjectEntity getProject(String tenantCode, String projectKey) {
        return projectRepo.findByTenantCodeAndProjectKey(tenantCode, projectKey).orElseThrow(
                () -> new IllegalArgumentException(
                        "Project not found: " + tenantCode + "/" + projectKey));
    }

    public ProjectEntity createProject(ProjectEntity p) {
        if (p.getTenantCode() == null || p.getProjectKey() == null) {
            throw new IllegalArgumentException("tenantCode and projectKey are required.");
        }
        if (!tenantRepo.existsByTenantCode(p.getTenantCode())) {
            throw new IllegalArgumentException("Unknown tenant: " + p.getTenantCode());
        }
        if (projectRepo.existsByTenantCodeAndProjectKey(p.getTenantCode(), p.getProjectKey())) {
            throw new IllegalArgumentException(
                    "Project already exists: " + p.getTenantCode() + "/" + p.getProjectKey());
        }
        return projectRepo.save(p);
    }
}
