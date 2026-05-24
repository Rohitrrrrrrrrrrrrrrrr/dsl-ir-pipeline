package com.dslpipeline.controller;

import com.dslpipeline.entity.ConfigDatabagEntity;
import com.dslpipeline.service.DataBagService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for config_databag — declared runtime variables.
 *
 * @author Nikunj Malik
 */
@RestController
@RequestMapping("/api/databags")
public class DataBagController {

    private final DataBagService service;

    public DataBagController(DataBagService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConfigDatabagEntity> list(@RequestParam String tenant,
                                          @RequestParam String project) {
        return service.list(tenant, project);
    }

    @GetMapping("/{id}")
    public ConfigDatabagEntity get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ConfigDatabagEntity create(@RequestBody ConfigDatabagEntity body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public ConfigDatabagEntity update(@PathVariable Long id,
                                      @RequestBody ConfigDatabagEntity body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    /** All declared databag fields for a project. */
    @GetMapping("/{tenant}/{project}/fields")
    public List<Map<String, Object>> fields(@PathVariable String tenant,
                                            @PathVariable String project) {
        return service.fieldsFor(tenant, project);
    }

    /** Preview the payload after databag defaults are seeded. */
    @PostMapping("/seed-preview")
    @SuppressWarnings("unchecked")
    public Map<String, Object> seedPreview(@RequestBody Map<String, Object> body) {
        String tenant = (String) body.get("tenant");
        String project = (String) body.get("project");
        Map<String, Object> payload = (Map<String, Object>) body.get("payload");
        return service.seedInto(payload, tenant, project);
    }
}
