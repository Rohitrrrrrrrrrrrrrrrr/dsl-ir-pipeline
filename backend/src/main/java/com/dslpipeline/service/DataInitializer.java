package com.dslpipeline.service;

import com.dslpipeline.entity.*;
import com.dslpipeline.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a default tenant / project / active schema / databag / custom function
 * on first startup so the system is demo-ready and QA has data to work with.
 *
 * Runs as a {@link CommandLineRunner} — i.e. BEFORE {@code ApplicationReadyEvent},
 * so {@code CustomFunctionService.onReady()} picks up the seeded function.
 * Idempotent: re-running does nothing once the rows exist.
 *
 * @author Nikunj Malik
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    public static final String TENANT = "acme";
    public static final String PROJECT = "lending";

    private final TenantRepository tenantRepo;
    private final ProjectRepository projectRepo;
    private final ConfigSchemaRepository schemaRepo;
    private final ConfigDatabagRepository databagRepo;
    private final CustomFunctionRepository functionRepo;

    public DataInitializer(TenantRepository tenantRepo, ProjectRepository projectRepo,
                           ConfigSchemaRepository schemaRepo, ConfigDatabagRepository databagRepo,
                           CustomFunctionRepository functionRepo) {
        this.tenantRepo = tenantRepo;
        this.projectRepo = projectRepo;
        this.schemaRepo = schemaRepo;
        this.databagRepo = databagRepo;
        this.functionRepo = functionRepo;
    }

    @Override
    public void run(String... args) {
        if (tenantRepo.existsByTenantCode(TENANT)) {
            log.info("Seed data already present — skipping initialisation.");
            return;
        }

        TenantEntity tenant = new TenantEntity();
        tenant.setTenantCode(TENANT);
        tenant.setName("Acme Insurance");
        tenant.setLlmConfig("{\"provider\":\"anthropic\",\"model\":\"claude-sonnet-4-5-20250929\"}");
        tenantRepo.save(tenant);

        ProjectEntity project = new ProjectEntity();
        project.setTenantCode(TENANT);
        project.setProjectKey(PROJECT);
        project.setName("Consumer Lending");
        projectRepo.save(project);

        ConfigSchemaEntity schema = new ConfigSchemaEntity();
        schema.setTenantCode(TENANT);
        schema.setProjectKey(PROJECT);
        schema.setVersionTag("v1");
        schema.setStatus("ACTIVE");
        schema.setSchemaJson("""
                {
                  "customer.age": "number",
                  "customer.income": "number",
                  "customer.region": "string",
                  "customer.creditScore": "number",
                  "claim.amount": "decimal",
                  "override.approved": "boolean",
                  "applicant.dateOfBirth": "date",
                  "loan.startDate": "date",
                  "dataBag.discountRate": "decimal",
                  "dataBag.baseRate": "decimal"
                }""");
        schema.setNotes("Default lending domain schema.");
        schemaRepo.save(schema);

        ConfigDatabagEntity databag = new ConfigDatabagEntity();
        databag.setTenantCode(TENANT);
        databag.setProjectKey(PROJECT);
        databag.setName("pricing");
        databag.setDescription("Runtime pricing variables.");
        databag.setStatus("active");
        databag.setFields("""
                [
                  { "path": "discountRate", "type": "decimal", "defaultValue": 0.10,
                    "description": "Default discount rate" },
                  { "path": "baseRate", "type": "decimal", "defaultValue": 0.05,
                    "description": "Base interest rate" }
                ]""");
        databagRepo.save(databag);

        CustomFunctionEntity fn = new CustomFunctionEntity();
        fn.setTenantId(TENANT);
        fn.setProjectId(PROJECT);
        fn.setNamespace("custom");
        fn.setFunctionName("incomeRiskBand");
        fn.setDescription("Maps an income figure to a risk band.");
        fn.setParameters("[{\"name\":\"income\",\"type\":\"number\"}]");
        fn.setReturnType("string");
        fn.setBodyExpression(
                "#income >= 100000 ? 'LOW' : (#income >= 50000 ? 'MEDIUM' : 'HIGH')");
        fn.setStatus("active");
        functionRepo.save(fn);

        log.info("Seed data created: tenant '{}', project '{}', 1 schema, 1 databag, 1 custom function.",
                TENANT, PROJECT);
    }
}
