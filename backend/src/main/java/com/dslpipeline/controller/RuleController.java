package com.dslpipeline.controller;

import com.dslpipeline.entity.RuleDefinitionEntity;
import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.service.RuleRepositoryService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for authored rules — the faithful three-table model
 * (rule_definition → rule_dsl_artifact → rule_ir_artifact).
 *
 * @author Nikunj Malik
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleRepositoryService rules;

    public RuleController(RuleRepositoryService rules) {
        this.rules = rules;
    }

    @GetMapping
    public List<RuleDefinitionEntity> list(@RequestParam String tenant,
                                           @RequestParam String project) {
        return rules.list(tenant, project);
    }

    @GetMapping("/{tenant}/{project}/{ruleKey}")
    public Map<String, Object> get(@PathVariable String tenant,
                                   @PathVariable String project,
                                   @PathVariable String ruleKey) {
        RuleDefinitionEntity rd = rules.get(tenant, project, "default", ruleKey);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("definition", rd);
        out.put("dslHistory", rules.dslHistory(rd.getRuleId()));
        out.put("irHistory", rules.irHistory(rd.getRuleId()));
        return out;
    }

    @PostMapping("/{tenant}/{project}/{ruleKey}/execute")
    public IrExecutor.ExecutionResult execute(@PathVariable String tenant,
                                              @PathVariable String project,
                                              @PathVariable String ruleKey,
                                              @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) body.get("payload");
        return rules.execute(tenant, project, "default", ruleKey, payload);
    }
}
