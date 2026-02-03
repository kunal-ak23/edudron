package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.identity.domain.Client;
import com.datagami.edudron.identity.domain.User;
import com.datagami.edudron.identity.dto.ClientDTO;
import com.datagami.edudron.identity.dto.CreateClientRequest;
import com.datagami.edudron.identity.repo.ClientRepository;
import com.datagami.edudron.identity.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClientService {
    
    private static final Logger log = LoggerFactory.getLogger(ClientService.class);
    
    @Autowired
    private ClientRepository clientRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private IdentityAuditService auditService;
    
    public ClientDTO createClient(CreateClientRequest request) {
        log.info("Creating new client: {}", request.getName());
        
        // SECURITY: Only SYSTEM_ADMIN can create tenants
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getRole() != User.Role.SYSTEM_ADMIN) {
            throw new IllegalArgumentException("Only SYSTEM_ADMIN can create tenants");
        }
        
        // Check if slug already exists
        if (clientRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new IllegalArgumentException("A client with slug '" + request.getSlug() + "' already exists");
        }
        
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setSlug(request.getSlug());
        client.setName(request.getName());
        client.setGstin(request.getGstin());
        client.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        client.setCreatedAt(java.time.OffsetDateTime.now());
        
        Client saved = clientRepository.save(client);
        log.info("Created client: {} with ID: {}", saved.getName(), saved.getId());
        String actorId = currentUser != null ? currentUser.getId() : null;
        String actorEmail = currentUser != null ? currentUser.getEmail() : null;
        auditService.logCrud("CREATE", "Client", saved.getId().toString(), actorId, actorEmail,
            java.util.Map.of("name", saved.getName(), "slug", saved.getSlug()));
        return toDTO(saved);
    }
    
    @Transactional(readOnly = true)
    public List<ClientDTO> getAllClients() {
        User currentUser = getCurrentUser();
        String clientIdStr = TenantContext.getClientId();
        
        // SYSTEM_ADMIN can see all tenants
        if (currentUser != null && currentUser.getRole() == User.Role.SYSTEM_ADMIN) {
            return clientRepository.findAllByOrderByName().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        }
        
        // Others see only their tenant
        if (clientIdStr != null && !clientIdStr.isBlank() && 
            !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            try {
                UUID clientId = UUID.fromString(clientIdStr);
                return clientRepository.findById(clientId)
                    .map(List::of)
                    .map(list -> list.stream().map(this::toDTO).collect(Collectors.toList()))
                    .orElse(List.of());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid clientId format: {}", clientIdStr);
                return List.of();
            }
        }
        
        return List.of();
    }
    
    @Transactional(readOnly = true)
    public List<ClientDTO> getActiveClients() {
        User currentUser = getCurrentUser();
        String clientIdStr = TenantContext.getClientId();
        
        // SYSTEM_ADMIN can see all active tenants
        if (currentUser != null && currentUser.getRole() == User.Role.SYSTEM_ADMIN) {
            return clientRepository.findByIsActiveTrueOrderByName().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        }
        
        // Others see only their tenant if active
        if (clientIdStr != null && !clientIdStr.isBlank() && 
            !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            try {
                UUID clientId = UUID.fromString(clientIdStr);
                return clientRepository.findById(clientId)
                    .filter(Client::getIsActive)
                    .map(List::of)
                    .map(list -> list.stream().map(this::toDTO).collect(Collectors.toList()))
                    .orElse(List.of());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid clientId format: {}", clientIdStr);
                return List.of();
            }
        }
        
        return List.of();
    }
    
    @Transactional(readOnly = true)
    public ClientDTO getClientById(UUID id) {
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + id));
        
        // Check tenant access for non-SYSTEM_ADMIN users
        User currentUser = getCurrentUser();
        String clientIdStr = TenantContext.getClientId();
        
        if (currentUser != null && currentUser.getRole() != User.Role.SYSTEM_ADMIN) {
            // Non-SYSTEM_ADMIN can only access their own tenant
            if (clientIdStr == null || clientIdStr.isBlank() || 
                "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
                throw new IllegalArgumentException("Client not found: " + id);
            }
            
            UUID clientId = UUID.fromString(clientIdStr);
            if (!client.getId().equals(clientId)) {
                throw new IllegalArgumentException("Client not found: " + id);
            }
        }
        
        return toDTO(client);
    }
    
    public ClientDTO updateClient(UUID id, CreateClientRequest request) {
        log.info("Updating client: {}", id);
        
        // SECURITY: Only SYSTEM_ADMIN can update tenants
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getRole() != User.Role.SYSTEM_ADMIN) {
            throw new IllegalArgumentException("Only SYSTEM_ADMIN can update tenants");
        }
        
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + id));
        
        // Check if slug is being changed and if new slug already exists
        if (!client.getSlug().equals(request.getSlug())) {
            if (clientRepository.findBySlug(request.getSlug()).isPresent()) {
                throw new IllegalArgumentException("A client with slug '" + request.getSlug() + "' already exists");
            }
        }
        
        client.setName(request.getName());
        client.setSlug(request.getSlug());
        client.setGstin(request.getGstin());
        if (request.getIsActive() != null) {
            client.setIsActive(request.getIsActive());
        }
        
        Client saved = clientRepository.save(client);
        log.info("Updated client: {}", saved.getId());
        String actorId = currentUser != null ? currentUser.getId() : null;
        String actorEmail = currentUser != null ? currentUser.getEmail() : null;
        auditService.logCrud("UPDATE", "Client", id.toString(), actorId, actorEmail,
            java.util.Map.of("name", saved.getName(), "slug", saved.getSlug()));
        return toDTO(saved);
    }
    
    public void deleteClient(UUID id) {
        log.info("Deleting client: {}", id);
        
        // SECURITY: Only SYSTEM_ADMIN can delete tenants
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getRole() != User.Role.SYSTEM_ADMIN) {
            throw new IllegalArgumentException("Only SYSTEM_ADMIN can delete tenants");
        }
        
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + id));
        
        // Soft delete by setting isActive to false
        client.setIsActive(false);
        clientRepository.save(client);
        String actorId = currentUser != null ? currentUser.getId() : null;
        String actorEmail = currentUser != null ? currentUser.getEmail() : null;
        auditService.logCrud("DELETE", "Client", id.toString(), actorId, actorEmail,
            java.util.Map.of("name", client.getName()));
        log.info("Soft deleted client: {}", id);
    }
    
    /**
     * Get the current authenticated user
     * Similar implementation to UserService.getCurrentUser()
     */
    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getPrincipal() == null) {
                log.warn("No authentication found in SecurityContext");
                return null;
            }
            
            String email = authentication.getName();
            if (email == null || email.isBlank()) {
                log.warn("Email is null or blank in authentication");
                return null;
            }
            
            String clientIdStr = TenantContext.getClientId();
            
            // Try to find user by email and tenant
            if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
                try {
                    UUID clientId = UUID.fromString(clientIdStr);
                    var user = userRepository.findByEmailAndClientId(email, clientId);
                    if (user.isPresent()) {
                        return user.get();
                    }
                    log.warn("User not found with email: {} and clientId: {}", email, clientId);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid clientId format: {}", clientIdStr);
                }
            }
            
            // Try SYSTEM_ADMIN lookup
            var systemAdmin = userRepository.findByEmailAndRoleAndActiveTrue(email, User.Role.SYSTEM_ADMIN);
            if (systemAdmin.isPresent()) {
                log.debug("Found SYSTEM_ADMIN user with email: {}", email);
                return systemAdmin.get();
            }
            
            // Log detailed information for debugging
            var usersByEmail = userRepository.findByEmailAndActiveTrue(email);
            if (!usersByEmail.isEmpty()) {
                log.warn("User with email {} exists but not found for tenant {} or as SYSTEM_ADMIN. Found {} user(s) with this email in other tenants.", 
                    email, clientIdStr, usersByEmail.size());
            } else {
                log.warn("User not found with email: {} (checked tenant: {}, SYSTEM_ADMIN). User may not exist or be inactive.", email, clientIdStr);
            }
            
        } catch (Exception e) {
            log.error("Failed to get current user: {}", e.getMessage(), e);
        }
        return null;
    }
    
    private ClientDTO toDTO(Client client) {
        return new ClientDTO(
            client.getId(),
            client.getSlug(),
            client.getName(),
            client.getGstin(),
            client.getIsActive(),
            client.getCreatedAt()
        );
    }
}


