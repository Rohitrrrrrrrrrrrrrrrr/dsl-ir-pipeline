package com.dslpipeline.repository;

import com.dslpipeline.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** core_project repository. @author Nikunj Malik */
@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    List<ProjectEntity> findByTenantCode(String tenantCode);
    Optional<ProjectEntity> findByTenantCodeAndProjectKey(String tenantCode, String projectKey);
    boolean existsByTenantCodeAndProjectKey(String tenantCode, String projectKey);
}
