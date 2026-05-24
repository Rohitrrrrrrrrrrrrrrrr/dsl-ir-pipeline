package com.dslpipeline.controller;

import com.dslpipeline.entity.ProjectEntity;
import com.dslpipeline.entity.TenantEntity;
import com.dslpipeline.service.TenantProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for the multi-tenant backbone — core_tenant and core_project.
 *
 * @author Nikunj Malik
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantProjectService service;

    public TenantController(TenantProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<TenantEntity> listTenants() {
        return service.listTenants();
    }

    @GetMapping("/{tenantCode}")
    public TenantEntity getTenant(@PathVariable String tenantCode) {
        return service.getTenant(tenantCode);
    }

    @PostMapping
    public TenantEntity createTenant(@RequestBody TenantEntity body) {
        return service.createTenant(body);
    }

    @GetMapping("/{tenantCode}/projects")
    public List<ProjectEntity> listProjects(@PathVariable String tenantCode) {
        return service.listProjects(tenantCode);
    }

    @PostMapping("/{tenantCode}/projects")
    public ProjectEntity createProject(@PathVariable String tenantCode,
                                       @RequestBody ProjectEntity body) {
        body.setTenantCode(tenantCode);
        return service.createProject(body);
    }
}
