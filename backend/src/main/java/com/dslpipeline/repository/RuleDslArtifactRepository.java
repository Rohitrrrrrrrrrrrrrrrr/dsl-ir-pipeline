package com.dslpipeline.repository;

import com.dslpipeline.entity.RuleDslArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** rule_dsl_artifact repository. @author Nikunj Malik */
@Repository
public interface RuleDslArtifactRepository extends JpaRepository<RuleDslArtifactEntity, Long> {
    List<RuleDslArtifactEntity> findByRuleIdOrderByCreatedAtDesc(Long ruleId);
}
