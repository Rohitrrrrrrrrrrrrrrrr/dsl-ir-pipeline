package com.dslpipeline.controller;

import com.dslpipeline.entity.ConfigSchemaEntity;
import com.dslpipeline.service.SchemaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for config_schema — the domain/DAL contract, with a status lifecycle.
 *
 * @author Nikunj Malik
 */
@RestController
@RequestMapping("/api/schemas")
public class SchemaController {

    private final SchemaService service;

    public SchemaController(SchemaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConfigSchemaEntity> list(@RequestParam String tenant,
                                         @RequestParam String project) {
        return service.list(tenant, project);
    }

    @GetMapping("/{id}")
    public ConfigSchemaEntity get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ConfigSchemaEntity create(@RequestBody ConfigSchemaEntity body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public ConfigSchemaEntity update(@PathVariable Long id,
                                     @RequestBody ConfigSchemaEntity body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/transition")
    public ConfigSchemaEntity transition(@PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        return service.transition(id, body.get("status"));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    /** The effective ACTIVE schema for a tenant/project as a flat path→type map. */
    @GetMapping("/{tenant}/{project}/resolve")
    public Map<String, String> resolve(@PathVariable String tenant,
                                       @PathVariable String project) {
        return service.resolveActiveSchema(tenant, project);
    }
}
