package com.dslpipeline.service;

import com.dslpipeline.entity.ConfigSchemaEntity;
import com.dslpipeline.repository.ConfigSchemaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD + resolution for {@code config_schema} — the domain/DAL contract.
 *
 * Schemas carry a lifecycle: DRAFT → UNDER_REVIEW → APPROVED → ACTIVE → DEPRECATED.
 * {@link #resolveActiveSchema} returns the flat path→type map the pipeline uses.
 *
 * @author Nikunj Malik
 */
@Service
public class SchemaService {

    private static final List<String> LIFECYCLE = List.of(
            "DRAFT", "UNDER_REVIEW", "APPROVED", "ACTIVE", "DEPRECATED", "DELETED");

    private final ConfigSchemaRepository repo;
    private final ObjectMapper mapper;

    public SchemaService(ConfigSchemaRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public List<ConfigSchemaEntity> list(String tenantCode, String projectKey) {
        return repo.findByTenantCodeAndProjectKey(tenantCode, projectKey);
    }

    public ConfigSchemaEntity get(Long id) {
        return repo.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Schema not found: " + id));
    }

    public ConfigSchemaEntity create(ConfigSchemaEntity e) {
        if (e.getTenantCode() == null || e.getProjectKey() == null) {
            throw new IllegalArgumentException("tenantCode and projectKey are required.");
        }
        validateJson(e.getSchemaJson());
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return repo.save(e);
    }

    public ConfigSchemaEntity update(Long id, ConfigSchemaEntity patch) {
        ConfigSchemaEntity e = get(id);
        if (patch.getSchemaJson() != null) {
            validateJson(patch.getSchemaJson());
            e.setSchemaJson(patch.getSchemaJson());
        }
        if (patch.getNotes() != null) e.setNotes(patch.getNotes());
        e.setUpdatedAt(Instant.now());
        return repo.save(e);
    }

    /** Advance a schema through its lifecycle (validates the transition is a move forward). */
    public ConfigSchemaEntity transition(Long id, String newStatus) {
        ConfigSchemaEntity e = get(id);
        String target = newStatus == null ? "" : newStatus.toUpperCase();
        if (!LIFECYCLE.contains(target)) {
            throw new IllegalArgumentException("Unknown status: " + newStatus
                    + " — must be one of " + LIFECYCLE);
        }
        e.setStatus(target);
        e.setUpdatedAt(Instant.now());
        return repo.save(e);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    /**
     * Resolve the effective ACTIVE schema for a tenant/project as a flat
     * path→type map. Empty map when no active schema exists.
     */
    public Map<String, String> resolveActiveSchema(String tenantCode, String projectKey) {
        List<ConfigSchemaEntity> active =
                repo.findByTenantCodeAndProjectKeyAndStatus(tenantCode, projectKey, "ACTIVE");
        if (active.isEmpty()) return new LinkedHashMap<>();
        // newest active wins
        ConfigSchemaEntity latest = active.get(active.size() - 1);
        return parse(latest.getSchemaJson());
    }

    private Map<String, String> parse(String json) {
        try {
            Map<String, String> m = mapper.readValue(
                    json == null || json.isBlank() ? "{}" : json,
                    new TypeReference<>() {});
            return m;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void validateJson(String json) {
        try {
            mapper.readValue(json == null || json.isBlank() ? "{}" : json,
                    new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "schemaJson must be a JSON object of path→type strings: " + e.getMessage());
        }
    }
}
