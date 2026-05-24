package com.dslpipeline;

import com.dslpipeline.entity.CustomFunctionEntity;
import com.dslpipeline.entity.ProjectEntity;
import com.dslpipeline.entity.RuleDefinitionEntity;
import com.dslpipeline.entity.TenantEntity;
import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.extensions.ExtensionRegistry;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.model.sl.StructuredLogic;
import com.dslpipeline.pipeline.IrBuilder;
import com.dslpipeline.pipeline.NlToSlConverter;
import com.dslpipeline.pipeline.SlToDslCompiler;
import com.dslpipeline.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep-debug tests for the data layer — each faithful stage exercised end to end:
 * tenant/project backbone, config_schema, config_databag, custom_extension_function
 * (SpEL), and the three-table rule persistence (rule_definition / dsl / ir).
 *
 * @author Nikunj Malik
 */
@SpringBootTest
class DataLayerTest {

    @Autowired TenantProjectService tenantProjects;
    @Autowired SchemaService schemaService;
    @Autowired DataBagService dataBagService;
    @Autowired CustomFunctionService customFunctions;
    @Autowired ExtensionRegistry registry;
    @Autowired RuleRepositoryService ruleRepository;
    @Autowired NlToSlConverter nlToSl;
    @Autowired SlToDslCompiler slToDsl;
    @Autowired IrBuilder irBuilder;

    private static final String TENANT = DataInitializer.TENANT;     // "acme"
    private static final String PROJECT = DataInitializer.PROJECT;   // "lending"

    // ── stage: tenant / project backbone ──

    @Test
    void seed_tenant_and_project_present() {
        TenantEntity t = tenantProjects.getTenant(TENANT);
        assertEquals("Acme Insurance", t.getName());
        List<ProjectEntity> projects = tenantProjects.listProjects(TENANT);
        assertTrue(projects.stream().anyMatch(p -> p.getProjectKey().equals(PROJECT)));
    }

    @Test
    void tenant_creation_rejects_duplicates() {
        assertThrows(IllegalArgumentException.class, () -> {
            TenantEntity dup = new TenantEntity();
            dup.setTenantCode(TENANT);
            dup.setName("dup");
            tenantProjects.createTenant(dup);
        });
    }

    // ── stage: config_schema ──

    @Test
    void schema_service_resolves_active_schema() {
        Map<String, String> schema = schemaService.resolveActiveSchema(TENANT, PROJECT);
        assertEquals("number", schema.get("customer.age"));
        assertEquals("decimal", schema.get("claim.amount"));
        assertEquals("boolean", schema.get("override.approved"));
    }

    // ── stage: config_databag ──

    @Test
    void databag_seeds_declared_defaults_into_payload() {
        Map<String, Object> seeded = dataBagService.seedInto(
                Map.of("customer", Map.of("age", 30)), TENANT, PROJECT);
        assertTrue(seeded.containsKey("dataBag"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bag = (Map<String, Object>) seeded.get("dataBag");
        assertEquals(0.10, ((Number) bag.get("discountRate")).doubleValue(), 1e-9);
        assertEquals(0.05, ((Number) bag.get("baseRate")).doubleValue(), 1e-9);
        // original data preserved
        assertTrue(seeded.containsKey("customer"));
    }

    @Test
    void databag_does_not_overwrite_supplied_values() {
        Map<String, Object> seeded = dataBagService.seedInto(
                Map.of("dataBag", Map.of("discountRate", 0.25)), TENANT, PROJECT);
        @SuppressWarnings("unchecked")
        Map<String, Object> bag = (Map<String, Object>) seeded.get("dataBag");
        assertEquals(0.25, ((Number) bag.get("discountRate")).doubleValue(), 1e-9);
        assertEquals(0.05, ((Number) bag.get("baseRate")).doubleValue(), 1e-9);
    }

    // ── stage: custom_extension_function (SpEL) ──

    @Test
    void custom_function_registered_and_callable() {
        assertTrue(registry.has("custom.incomeRiskBand"),
                "seeded custom function should be in the registry");
        assertEquals("LOW", registry.resolve("custom.incomeRiskBand")
                .invoke(List.of(120_000)));
        assertEquals("MEDIUM", registry.resolve("custom.incomeRiskBand")
                .invoke(List.of(60_000)));
        assertEquals("HIGH", registry.resolve("custom.incomeRiskBand")
                .invoke(List.of(20_000)));
    }

    @Test
    void custom_function_crud_reloads_registry() {
        CustomFunctionEntity fn = new CustomFunctionEntity();
        fn.setTenantId(TENANT);
        fn.setProjectId(PROJECT);
        fn.setNamespace("custom");
        fn.setFunctionName("doubleIt");
        fn.setParameters("[{\"name\":\"x\",\"type\":\"number\"}]");
        fn.setReturnType("number");
        fn.setBodyExpression("#x * 2");
        fn.setStatus("active");
        customFunctions.create(fn);

        assertTrue(registry.has("custom.doubleIt"));
        Object out = registry.resolve("custom.doubleIt").invoke(List.of(21));
        assertEquals(42, ((Number) out).intValue());
    }

    @Test
    void custom_function_rejects_bad_spel() {
        CustomFunctionEntity fn = new CustomFunctionEntity();
        fn.setTenantId(TENANT);
        fn.setNamespace("custom");
        fn.setFunctionName("broken");
        fn.setBodyExpression("#x +* 2");   // invalid SpEL
        assertThrows(IllegalArgumentException.class, () -> customFunctions.create(fn));
    }

    // ── stage: three-table rule persistence ──

    @Test
    void rule_persisted_across_three_tables_and_executes() {
        StructuredLogic sl = nlToSl.convert("customer age < 18 decline the loan", "rule");
        RuleDSL dsl = slToDsl.compile(sl);
        CanonicalIR ir = irBuilder.build(dsl);

        RuleDefinitionEntity rd = ruleRepository.save(
                TENANT, PROJECT, "default", "AGE_UNDER_18", "Age under 18 decline", dsl, ir);

        assertNotNull(rd.getRuleId());
        assertEquals("AGE_UNDER_18", rd.getRuleKey());
        assertFalse(ruleRepository.dslHistory(rd.getRuleId()).isEmpty(),
                "a rule_dsl_artifact row should exist");
        assertFalse(ruleRepository.irHistory(rd.getRuleId()).isEmpty(),
                "a rule_ir_artifact row should exist");

        // execute the stored rule straight from its persisted IR
        IrExecutor.ExecutionResult minor = ruleRepository.execute(
                TENANT, PROJECT, "default", "AGE_UNDER_18", Map.of("customer", Map.of("age", 16)));
        assertTrue(minor.conditionsMet);
        assertFalse(minor.passed);
        assertEquals("LOAN_DECLINED", minor.errors.get(0).code);

        IrExecutor.ExecutionResult adult = ruleRepository.execute(
                TENANT, PROJECT, "default", "AGE_UNDER_18", Map.of("customer", Map.of("age", 30)));
        assertTrue(adult.passed);
    }

    @Test
    void saving_same_rule_key_bumps_version() {
        StructuredLogic sl = nlToSl.convert("customer age < 21 decline the loan", "rule");
        RuleDSL dsl = slToDsl.compile(sl);
        CanonicalIR ir = irBuilder.build(dsl);

        RuleDefinitionEntity v1 = ruleRepository.save(
                TENANT, PROJECT, "default", "VERSIONED_RULE", "v", dsl, ir);
        RuleDefinitionEntity v2 = ruleRepository.save(
                TENANT, PROJECT, "default", "VERSIONED_RULE", "v", dsl, ir);

        assertEquals(v1.getRuleId(), v2.getRuleId(), "same rule_definition row reused");
        assertEquals(v1.getVersion() + 1, v2.getVersion(), "version bumped on re-save");
        assertTrue(ruleRepository.irHistory(v2.getRuleId()).size() >= 2,
                "each save appends a new IR artifact");
    }
}
