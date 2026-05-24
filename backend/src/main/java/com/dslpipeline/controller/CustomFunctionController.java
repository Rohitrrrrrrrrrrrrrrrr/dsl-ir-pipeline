package com.dslpipeline.controller;

import com.dslpipeline.entity.CustomFunctionEntity;
import com.dslpipeline.service.CustomFunctionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for custom_extension_function — DB-backed SpEL project functions.
 *
 * @author Nikunj Malik
 */
@RestController
@RequestMapping("/api/functions")
public class CustomFunctionController {

    private final CustomFunctionService service;

    public CustomFunctionController(CustomFunctionService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomFunctionEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CustomFunctionEntity get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public CustomFunctionEntity create(@RequestBody CustomFunctionEntity body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public CustomFunctionEntity update(@PathVariable Long id,
                                       @RequestBody CustomFunctionEntity body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    /** Invoke a custom function ad-hoc with ordered arguments. */
    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> args = (List<Object>) body.getOrDefault("args", List.of());
        Object result = service.invokeForTest(id, args);
        return Map.of("args", args, "result", result);
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        service.reload();
        return Map.of("reloaded", true);
    }
}
