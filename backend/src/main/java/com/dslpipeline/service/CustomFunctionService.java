package com.dslpipeline.service;

import com.dslpipeline.entity.CustomFunctionEntity;
import com.dslpipeline.extensions.ExtensionFunction;
import com.dslpipeline.extensions.ExtensionRegistry;
import com.dslpipeline.repository.CustomFunctionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code custom_extension_function} rows, compiles their SpEL bodies into
 * {@link ExtensionFunction} instances and registers them into the
 * {@link ExtensionRegistry} — no deployment needed to add a project function.
 *
 * Parameter names are exposed to the SpEL body as {@code #name} variables; the
 * full argument list is exposed as {@code #args}.
 *
 * @author Nikunj Malik
 */
@Service
public class CustomFunctionService {

    private static final Logger log = LoggerFactory.getLogger(CustomFunctionService.class);
    private static final SpelExpressionParser SPEL = new SpelExpressionParser();

    private final CustomFunctionRepository repo;
    private final ExtensionRegistry registry;
    private final ObjectMapper mapper;

    public CustomFunctionService(CustomFunctionRepository repo, ExtensionRegistry registry,
                                 ObjectMapper mapper) {
        this.repo = repo;
        this.registry = registry;
        this.mapper = mapper;
    }

    /** Register all active custom functions once the context is ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reload();
    }

    /** Rebuild the custom-function set in the registry from the DB. */
    public synchronized void reload() {
        registry.clearCustomFunctions();
        int ok = 0, failed = 0;
        for (CustomFunctionEntity e : repo.findByStatus("active")) {
            try {
                registry.registerCustomFunction(compile(e));
                ok++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Skipped custom function '{}': {}", e.qualifiedName(), ex.getMessage());
            }
        }
        log.info("Custom functions reloaded: {} registered, {} skipped", ok, failed);
    }

    // ─────────────────────────── CRUD ───────────────────────────

    public List<CustomFunctionEntity> list() {
        return repo.findAll();
    }

    public CustomFunctionEntity get(Long id) {
        return repo.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Custom function not found: " + id));
    }

    public CustomFunctionEntity create(CustomFunctionEntity e) {
        validate(e);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        CustomFunctionEntity saved = repo.save(e);
        reload();
        return saved;
    }

    public CustomFunctionEntity update(Long id, CustomFunctionEntity patch) {
        CustomFunctionEntity e = get(id);
        if (patch.getDescription() != null) e.setDescription(patch.getDescription());
        if (patch.getParameters() != null) e.setParameters(patch.getParameters());
        if (patch.getReturnType() != null) e.setReturnType(patch.getReturnType());
        if (patch.getBodyExpression() != null) e.setBodyExpression(patch.getBodyExpression());
        if (patch.getStatus() != null) e.setStatus(patch.getStatus());
        e.setUpdatedAt(Instant.now());
        validate(e);
        CustomFunctionEntity saved = repo.save(e);
        reload();
        return saved;
    }

    public void delete(Long id) {
        repo.deleteById(id);
        reload();
    }

    /** Validate (and SpEL-compile) a function definition; throws on any problem. */
    public void validate(CustomFunctionEntity e) {
        if (e.getFunctionName() == null || e.getFunctionName().isBlank()) {
            throw new IllegalArgumentException("functionName is required.");
        }
        if (e.getBodyExpression() == null || e.getBodyExpression().isBlank()) {
            throw new IllegalArgumentException("bodyExpression (SpEL) is required.");
        }
        // compile to surface SpEL syntax errors early
        compile(e);
    }

    /** Invoke a custom function ad-hoc with ordered args (used for test runs). */
    public Object invokeForTest(Long id, List<Object> args) {
        return compile(get(id)).invoke(args);
    }

    // ─────────────────────────── compilation ───────────────────────────

    /** Build a runtime {@link ExtensionFunction} from a DB row. */
    public ExtensionFunction compile(CustomFunctionEntity e) {
        List<String> paramNames = new ArrayList<>();
        List<String> argTypes = new ArrayList<>();
        try {
            List<Map<String, Object>> params = mapper.readValue(
                    e.getParameters() == null ? "[]" : e.getParameters(),
                    new TypeReference<>() {});
            for (Map<String, Object> p : params) {
                paramNames.add(String.valueOf(p.getOrDefault("name", "arg")));
                argTypes.add(String.valueOf(p.getOrDefault("type", "any")));
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("parameters is not valid JSON: " + ex.getMessage());
        }

        final Expression expr;
        try {
            expr = SPEL.parseExpression(e.getBodyExpression());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("SpEL parse error in body_expression: " + ex.getMessage());
        }

        ExtensionFunction.Impl impl = args -> {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            for (int i = 0; i < paramNames.size(); i++) {
                ctx.setVariable(paramNames.get(i), i < args.size() ? args.get(i) : null);
            }
            ctx.setVariable("args", args);
            try {
                return expr.getValue(ctx);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(
                        "custom function '" + e.qualifiedName() + "' failed: " + ex.getMessage());
            }
        };

        return new ExtensionFunction(
                e.qualifiedName(), e.getNamespace(), argTypes, e.getReturnType(),
                true, false,
                e.getDescription() == null ? "Custom SpEL function" : e.getDescription(),
                impl);
    }
}
