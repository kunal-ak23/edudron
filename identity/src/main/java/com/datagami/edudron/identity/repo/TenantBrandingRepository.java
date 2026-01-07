package com.datagami.edudron.identity.repo;

import com.datagami.edudron.identity.domain.TenantBranding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantBrandingRepository extends JpaRepository<TenantBranding, UUID> {
    Optional<TenantBranding> findByClientId(UUID clientId);
    Optional<TenantBranding> findByClientIdAndIsActiveTrue(UUID clientId);
}


