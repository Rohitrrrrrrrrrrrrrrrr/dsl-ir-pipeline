package com.dslpipeline.repository;

import com.dslpipeline.entity.RuleIrArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** rule_ir_artifact repository. @author Nikunj Malik */
@Repository
public interface RuleIrArtifactRepository extends JpaRepository<RuleIrArtifactEntity, Long> {
    List<RuleIrArtifactEntity> findByRuleIdOrderByCreatedAtDesc(Long ruleId);
}
