package com.dslpipeline.repository;

import com.dslpipeline.entity.ConfigDatabagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** config_databag repository. @author Nikunj Malik */
@Repository
public interface ConfigDatabagRepository extends JpaRepository<ConfigDatabagEntity, Long> {
    List<ConfigDatabagEntity> findByTenantCodeAndProjectKey(String tenantCode, String projectKey);
    Optional<ConfigDatabagEntity> findByTenantCodeAndProjectKeyAndName(
            String tenantCode, String projectKey, String name);
}
