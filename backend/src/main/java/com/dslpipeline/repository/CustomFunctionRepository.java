package com.dslpipeline.repository;

import com.dslpipeline.entity.CustomFunctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** custom_extension_function repository. @author Nikunj Malik */
@Repository
public interface CustomFunctionRepository extends JpaRepository<CustomFunctionEntity, Long> {
    List<CustomFunctionEntity> findByStatus(String status);
    List<CustomFunctionEntity> findByTenantIdAndProjectId(String tenantId, String projectId);
}
