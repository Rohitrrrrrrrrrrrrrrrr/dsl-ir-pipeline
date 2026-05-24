package com.dslpipeline.repository;

import com.dslpipeline.entity.ConfigSchemaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** config_schema repository. @author Nikunj Malik */
@Repository
public interface ConfigSchemaRepository extends JpaRepository<ConfigSchemaEntity, Long> {
    List<ConfigSchemaEntity> findByTenantCodeAndProjectKey(String tenantCode, String projectKey);
    List<ConfigSchemaEntity> findByTenantCodeAndProjectKeyAndStatus(
            String tenantCode, String projectKey, String status);
}
