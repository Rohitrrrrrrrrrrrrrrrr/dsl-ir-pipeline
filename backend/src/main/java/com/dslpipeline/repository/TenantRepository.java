package com.dslpipeline.repository;

import com.dslpipeline.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** core_tenant repository. @author Nikunj Malik */
@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, Long> {
    Optional<TenantEntity> findByTenantCode(String tenantCode);
    boolean existsByTenantCode(String tenantCode);
}
