package com.dslpipeline.service;

import com.dslpipeline.entity.RuleDefinitionEntity;
import com.dslpipeline.entity.RuleDslArtifactEntity;
import com.dslpipeline.entity.RuleIrArtifactEntity;
import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.model.dsl.RuleDSL;
import com.dslpipeline.model.ir.CanonicalIR;
import com.dslpipeline.repository.RuleDefinitionRepository;
import com.dslpipeline.repository.RuleDslArtifactRepository;
import com.dslpipeline.repository.RuleIrArtifactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persists and executes authored rules across the faithful three-table model:
 * {@code rule_definition} → {@code rule_dsl_artifact} → {@code rule_ir_artifact}.
 *
 * Saving a rule re-uses the existing {@code rule_definition} row (bumping its
 * version) and always appends fresh DSL + IR artifact rows, so history is kept.
 *
 * @author Nikunj Malik
 */
@Service
public class RuleRepositoryService {

    private final RuleDefinitionRepository ruleRepo;
    private final RuleDslArtifactRepository dslRepo;
    private final RuleIrArtifactRepository irRepo;
    private final IrExecutor irExecutor;
    private final DataBagService dataBagService;
    private final ObjectMapper mapper;

    public RuleRepositoryService(RuleDefinitionRepository ruleRepo,
                                 RuleDslArtifactRepository dslRepo,
                                 RuleIrArtifactRepository irRepo,
                                 IrExecutor irExecutor,
                                 DataBagService dataBagService,
                                 ObjectMapper mapper) {
        this.ruleRepo = ruleRepo;
        this.dslRepo = dslRepo;
        this.irRepo = irRepo;
        this.irExecutor = irExecutor;
        this.dataBagService = dataBagService;
        this.mapper = mapper;
    }

    // ─────────────────────────── persistence ───────────────────────────

    /**
     * Persist a compiled rule. Creates (or updates) the {@code rule_definition}
     * and appends new {@code rule_dsl_artifact} + {@code rule_ir_artifact} rows.
     */
    @Transactional
    public RuleDefinitionEntity save(String tenantCode, String projectKey, String namespace,
                                     String ruleKey, String name, RuleDSL dsl, CanonicalIR ir) {
        try {
            String ns = (namespace == null || namespace.isBlank()) ? "default" : namespace;
            RuleDefinitionEntity rd = ruleRepo
                    .findByTenantCodeAndProjectKeyAndNamespaceAndRuleKey(
                            tenantCode, projectKey, ns, ruleKey)
                    .orElseGet(RuleDefinitionEntity::new);

            boolean isNew = rd.getRuleId() == null;
            rd.setTenantCode(tenantCode);
            rd.setProjectKey(projectKey);
            rd.setNamespace(ns);
            rd.setRuleKey(ruleKey);
            rd.setName(name != null ? name : ruleKey);
            rd.setType("deterministic");
            rd.setPriority(dsl.getPriority());
            rd.setConditionsJson(mapper.writeValueAsString(dsl.getConditions()));
            rd.setOutcomesJson(mapper.writeValueAsString(dsl.getActions()));
            if (dsl.getMetadata() != null && dsl.getMetadata().get("originalNl") != null) {
                rd.setIntent(String.valueOf(dsl.getMetadata().get("originalNl")));
            }
            rd.setUpdatedAt(Instant.now());
            if (!isNew) {
                rd.setVersion(rd.getVersion() + 1);
                rd.setStatus("ready");
            }
            rd = ruleRepo.save(rd);

            String dslJson = mapper.writeValueAsString(dsl);
            RuleDslArtifactEntity dslArt = new RuleDslArtifactEntity();
            dslArt.setRuleId(rd.getRuleId());
            dslArt.setDslSpec(dslJson);
            dslArt.setPath("rules/" + ruleKey + "/dsl.json");
            dslArt.setHash(sha256(dslJson));
            dslArt = dslRepo.save(dslArt);

            String irJson = mapper.writeValueAsString(ir);
            RuleIrArtifactEntity irArt = new RuleIrArtifactEntity();
            irArt.setRuleId(rd.getRuleId());
            irArt.setSourceDslId(dslArt.getDslId());
            irArt.setIrSchema(irJson);
            irArt.setPath("rules/" + ruleKey + "/ir.json");
            irArt.setHash(sha256(irJson));
            irRepo.save(irArt);

            return rd;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist rule '" + ruleKey + "': "
                    + e.getMessage(), e);
        }
    }

    // ─────────────────────────── queries ───────────────────────────

    public List<RuleDefinitionEntity> list(String tenantCode, String projectKey) {
        return ruleRepo.findByTenantCodeAndProjectKey(tenantCode, projectKey);
    }

    public RuleDefinitionEntity get(String tenantCode, String projectKey,
                                    String namespace, String ruleKey) {
        String ns = (namespace == null || namespace.isBlank()) ? "default" : namespace;
        return ruleRepo.findByTenantCodeAndProjectKeyAndNamespaceAndRuleKey(
                        tenantCode, projectKey, ns, ruleKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rule not found: " + tenantCode + "/" + projectKey + "/" + ns + "/" + ruleKey));
    }

    public List<RuleDslArtifactEntity> dslHistory(Long ruleId) {
        return dslRepo.findByRuleIdOrderByCreatedAtDesc(ruleId);
    }

    public List<RuleIrArtifactEntity> irHistory(Long ruleId) {
        return irRepo.findByRuleIdOrderByCreatedAtDesc(ruleId);
    }

    /** Load the latest compiled IR for a stored rule. */
    public CanonicalIR latestIr(Long ruleId) {
        List<RuleIrArtifactEntity> irs = irRepo.findByRuleIdOrderByCreatedAtDesc(ruleId);
        if (irs.isEmpty()) {
            throw new IllegalStateException("Rule " + ruleId + " has no compiled IR artifact.");
        }
        try {
            return mapper.readValue(irs.get(0).getIrSchema(), CanonicalIR.class);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt IR artifact for rule " + ruleId + ": "
                    + e.getMessage(), e);
        }
    }

    // ─────────────────────────── execution ───────────────────────────

    /**
     * Execute a stored rule against a payload — loads the latest IR, seeds the
     * project's databag defaults, then runs the deterministic interpreter.
     */
    public IrExecutor.ExecutionResult execute(String tenantCode, String projectKey,
                                              String namespace, String ruleKey,
                                              Map<String, Object> payload) {
        RuleDefinitionEntity rd = get(tenantCode, projectKey, namespace, ruleKey);
        CanonicalIR ir = latestIr(rd.getRuleId());
        Map<String, Object> seeded = dataBagService.seedInto(payload, tenantCode, projectKey);
        return irExecutor.execute(ir, seeded);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((s == null ? "" : s).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "0".repeat(64);
        }
    }
}
