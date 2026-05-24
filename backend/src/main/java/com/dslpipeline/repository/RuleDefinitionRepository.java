package com.dslpipeline.repository;

import com.dslpipeline.entity.RuleDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** rule_definition repository. @author Nikunj Malik */
@Repository
public interface RuleDefinitionRepository extends JpaRepository<RuleDefinitionEntity, Long> {

    List<RuleDefinitionEntity> findByTenantCodeAndProjectKey(String tenantCode, String projectKey);

    Optional<RuleDefinitionEntity> findByTenantCodeAndProjectKeyAndNamespaceAndRuleKey(
            String tenantCode, String projectKey, String namespace, String ruleKey);

    boolean existsByTenantCodeAndProjectKeyAndNamespaceAndRuleKey(
            String tenantCode, String projectKey, String namespace, String ruleKey);
}
