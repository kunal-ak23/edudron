package com.datagami.edudron.identity.repo;

import com.datagami.edudron.identity.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    // Case-sensitive methods (kept for backward compatibility, but prefer IgnoreCase versions)
    Optional<User> findByEmailAndClientId(String email, UUID clientId);
    Optional<User> findByEmailAndClientIdAndActiveTrue(String email, UUID clientId);
    boolean existsByEmailAndClientId(String email, UUID clientId);
    
    // Case-insensitive methods (recommended for email lookups)
    Optional<User> findByEmailIgnoreCaseAndClientId(String email, UUID clientId);
    Optional<User> findByEmailIgnoreCaseAndClientIdAndActiveTrue(String email, UUID clientId);
    boolean existsByEmailIgnoreCaseAndClientId(String email, UUID clientId);
    
    // Find all users with the same email across all tenants
    List<User> findByEmailAndActiveTrue(String email);
    List<User> findByEmailIgnoreCaseAndActiveTrue(String email);
    
    // Find SYSTEM_ADMIN user by email only (no tenant context)
    Optional<User> findByEmailAndRoleAndActiveTrue(String email, User.Role role);
    Optional<User> findByEmailIgnoreCaseAndRoleAndActiveTrue(String email, User.Role role);
    boolean existsByEmailAndRole(String email, User.Role role);
    boolean existsByEmailIgnoreCaseAndRole(String email, User.Role role);
    
    // Find all active users for a specific tenant
    List<User> findByClientIdAndActiveTrue(UUID clientId);
    
    // Find all users for a specific tenant (active and inactive)
    List<User> findByClientId(UUID clientId);
    
    // Find users by tenant and role
    List<User> findByClientIdAndRole(UUID clientId, User.Role role);
    List<User> findByClientIdAndRoleAndActiveTrue(UUID clientId, User.Role role);
    
    // Paginated queries
    Page<User> findByClientIdAndRole(UUID clientId, User.Role role, Pageable pageable);
    Page<User> findByRole(User.Role role, Pageable pageable);

    // Counts (used for dashboards / stats)
    long countByClientIdAndRole(UUID clientId, User.Role role);
    long countByClientIdAndRoleAndActiveTrue(UUID clientId, User.Role role);
    long countByRole(User.Role role);
    long countByRoleAndActiveTrue(User.Role role);
}

