package com.dslpipeline.repository;

import com.dslpipeline.entity.IrArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * IR artifact persistence — H2-backed.
 *
 * @author Nikunj Malik
 */
@Repository
public interface IrArtifactRepository extends JpaRepository<IrArtifactEntity, Long> {
    List<IrArtifactEntity> findByRuleIdOrderByCompiledAtDesc(String ruleId);
}
