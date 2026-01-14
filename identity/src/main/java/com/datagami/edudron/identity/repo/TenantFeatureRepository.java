package com.datagami.edudron.identity.repo;

import com.datagami.edudron.identity.domain.TenantFeatureType;
import com.datagami.edudron.identity.entity.TenantFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantFeatureRepository extends JpaRepository<TenantFeature, UUID> {
    
    /**
     * Find all feature overrides for a specific tenant.
     */
    List<TenantFeature> findByClientId(UUID clientId);
    
    /**
     * Find a specific feature override for a tenant.
     */
    Optional<TenantFeature> findByClientIdAndFeature(UUID clientId, TenantFeatureType feature);
    
    /**
     * Check if a feature override exists for a tenant.
     */
    boolean existsByClientIdAndFeature(UUID clientId, TenantFeatureType feature);
    
    /**
     * Delete a feature override for a tenant (resets to default).
     */
    void deleteByClientIdAndFeature(UUID clientId, TenantFeatureType feature);
}
