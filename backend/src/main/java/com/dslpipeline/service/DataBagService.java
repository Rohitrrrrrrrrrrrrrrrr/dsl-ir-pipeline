package com.dslpipeline.service;

import com.dslpipeline.entity.ConfigDatabagEntity;
import com.dslpipeline.repository.ConfigDatabagRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD for {@code config_databag} + runtime seeding.
 *
 * A databag DECLARES fields ({@code path, type, defaultValue}); it is not runtime
 * state. {@link #seedInto} writes the declared defaults into
 * {@code payload["dataBag"]} at execution start, so rules can read/write them.
 *
 * @author Nikunj Malik
 */
@Service
public class DataBagService {

    private final ConfigDatabagRepository repo;
    private final ObjectMapper mapper;

    public DataBagService(ConfigDatabagRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public List<ConfigDatabagEntity> list(String tenantCode, String projectKey) {
        return repo.findByTenantCodeAndProjectKey(tenantCode, projectKey);
    }

    public ConfigDatabagEntity get(Long id) {
        return repo.findById(id).orElseThrow(
                () -> new IllegalArgumentException("DataBag not found: " + id));
    }

    public ConfigDatabagEntity create(ConfigDatabagEntity e) {
        if (e.getTenantCode() == null || e.getProjectKey() == null || e.getName() == null) {
            throw new IllegalArgumentException("tenantCode, projectKey and name are required.");
        }
        validateFields(e.getFields());
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return repo.save(e);
    }

    public ConfigDatabagEntity update(Long id, ConfigDatabagEntity patch) {
        ConfigDatabagEntity e = get(id);
        if (patch.getFields() != null) {
            validateFields(patch.getFields());
            e.setFields(patch.getFields());
        }
        if (patch.getDescription() != null) e.setDescription(patch.getDescription());
        if (patch.getStatus() != null) e.setStatus(patch.getStatus());
        e.setUpdatedAt(Instant.now());
        return repo.save(e);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    /** All declared databag fields for a project (across every active databag). */
    public List<Map<String, Object>> fieldsFor(String tenantCode, String projectKey) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConfigDatabagEntity bag : repo.findByTenantCodeAndProjectKey(tenantCode, projectKey)) {
            if (!"active".equalsIgnoreCase(bag.getStatus())) continue;
            out.addAll(parseFields(bag.getFields()));
        }
        return out;
    }

    /**
     * Seed declared databag defaults into {@code payload["dataBag"]}.
     * Returns a new payload map (the input is not mutated).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> seedInto(Map<String, Object> payload,
                                        String tenantCode, String projectKey) {
        Map<String, Object> result = payload == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        Map<String, Object> dataBag = result.get("dataBag") instanceof Map<?, ?> existing
                ? new LinkedHashMap<>((Map<String, Object>) existing)
                : new LinkedHashMap<>();
        for (Map<String, Object> field : fieldsFor(tenantCode, projectKey)) {
            String path = String.valueOf(field.get("path"));
            if (path.isBlank() || "null".equals(path)) continue;
            if (!dataBag.containsKey(path) && field.containsKey("defaultValue")) {
                dataBag.put(path, field.get("defaultValue"));
            }
        }
        if (!dataBag.isEmpty()) result.put("dataBag", dataBag);
        return result;
    }

    private List<Map<String, Object>> parseFields(String json) {
        try {
            return mapper.readValue(json == null || json.isBlank() ? "[]" : json,
                    new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void validateFields(String json) {
        try {
            List<Map<String, Object>> fields = mapper.readValue(
                    json == null || json.isBlank() ? "[]" : json, new TypeReference<>() {});
            for (Map<String, Object> f : fields) {
                if (f.get("path") == null || String.valueOf(f.get("path")).isBlank()) {
                    throw new IllegalArgumentException("every databag field needs a 'path'.");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "fields must be a JSON array of {path,type,defaultValue}: " + e.getMessage());
        }
    }
}
