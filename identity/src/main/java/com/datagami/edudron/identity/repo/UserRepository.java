package com.datagami.edudron.identity.repo;

import com.datagami.edudron.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmailAndClientId(String email, UUID clientId);
    Optional<User> findByEmailAndClientIdAndActiveTrue(String email, UUID clientId);
    boolean existsByEmailAndClientId(String email, UUID clientId);
    
    // Find all users with the same email across all tenants
    List<User> findByEmailAndActiveTrue(String email);
    
    // Find SYSTEM_ADMIN user by email only (no tenant context)
    Optional<User> findByEmailAndRoleAndActiveTrue(String email, User.Role role);
    boolean existsByEmailAndRole(String email, User.Role role);
    
    // Find all active users for a specific tenant
    List<User> findByClientIdAndActiveTrue(UUID clientId);
    
    // Find all users for a specific tenant (active and inactive)
    List<User> findByClientId(UUID clientId);
    
    // Find users by tenant and role
    List<User> findByClientIdAndRole(UUID clientId, User.Role role);
    List<User> findByClientIdAndRoleAndActiveTrue(UUID clientId, User.Role role);
}

