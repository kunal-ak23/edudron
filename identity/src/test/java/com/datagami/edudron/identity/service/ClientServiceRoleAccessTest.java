package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.identity.domain.Client;
import com.datagami.edudron.identity.domain.User;
import com.datagami.edudron.identity.dto.CreateClientRequest;
import com.datagami.edudron.identity.repo.ClientRepository;
import com.datagami.edudron.identity.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for ClientService role-based access control.
 * Covers all scenarios for tenant (client) management restrictions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClientService Role-Based Access Control Tests")
class ClientServiceRoleAccessTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private ClientService clientService;

    private UUID testClientId;
    private User currentUser;

    @BeforeEach
    void setUp() {
        testClientId = UUID.randomUUID();
        TenantContext.setClientId(testClientId.toString());
        
        // Setup SecurityContext mock
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        // Default: no authentication unless explicitly mocked in test
        lenient().when(authentication.getName()).thenReturn(null);
        lenient().when(authentication.getPrincipal()).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private User createMockUser(User.Role role, String email) {
        User user = new User();
        user.setId("user-" + UUID.randomUUID().toString());
        user.setClientId(testClientId);
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setName("Test User");
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private Client createMockClient(UUID id) {
        Client client = new Client();
        client.setId(id);
        client.setSlug("test-client");
        client.setName("Test Client");
        client.setIsActive(true);
        return client;
    }

    private void mockCurrentUser(User user) {
        currentUser = user;
        when(authentication.getName()).thenReturn(user.getEmail());
        when(authentication.getPrincipal()).thenReturn(user.getEmail());
        
        String clientIdStr = TenantContext.getClientId();
        
        // For SYSTEM_ADMIN users
        if (user.getRole() == User.Role.SYSTEM_ADMIN) {
            // Always mock SYSTEM_ADMIN lookup (this is the fallback)
            lenient().when(userRepository.findByEmailAndRoleAndActiveTrue(user.getEmail(), User.Role.SYSTEM_ADMIN))
                .thenReturn(Optional.of(user));
            
            // If TenantContext is not "SYSTEM", also mock the tenant lookup (which will fail, then fallback to SYSTEM_ADMIN)
            if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
                try {
                    UUID clientId = UUID.fromString(clientIdStr);
                    lenient().when(userRepository.findByEmailAndClientId(user.getEmail(), clientId))
                        .thenReturn(Optional.empty()); // Will fail, then fallback to SYSTEM_ADMIN lookup
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        } else {
            // For non-SYSTEM_ADMIN users, mock tenant-based lookup
            if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
                try {
                    UUID clientId = UUID.fromString(clientIdStr);
                    lenient().when(userRepository.findByEmailAndClientId(user.getEmail(), clientId))
                        .thenReturn(Optional.of(user));
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        }
    }

    // ========== createClient() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can create tenants")
    void testSystemAdminCanCreateTenant() {
        TenantContext.setClientId("SYSTEM");
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("new-tenant");
        request.setName("New Tenant");
        request.setIsActive(true);

        when(clientRepository.findBySlug("new-tenant")).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> clientService.createClient(request),
            "SYSTEM_ADMIN should be able to create tenants");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot create tenants")
    void testTenantAdminCannotCreateTenant() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("new-tenant");
        request.setName("New Tenant");
        request.setIsActive(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.createClient(request),
            "TENANT_ADMIN should not be able to create tenants");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can create tenants"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    @Test
    @DisplayName("CONTENT_MANAGER cannot create tenants")
    void testContentManagerCannotCreateTenant() {
        User contentManager = createMockUser(User.Role.CONTENT_MANAGER, "content-manager@example.com");
        mockCurrentUser(contentManager);

        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("new-tenant");
        request.setName("New Tenant");
        request.setIsActive(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.createClient(request),
            "CONTENT_MANAGER should not be able to create tenants");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can create tenants"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot create tenants")
    void testInstructorCannotCreateTenant() {
        User instructor = createMockUser(User.Role.INSTRUCTOR, "instructor@example.com");
        mockCurrentUser(instructor);

        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("new-tenant");
        request.setName("New Tenant");
        request.setIsActive(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.createClient(request),
            "INSTRUCTOR should not be able to create tenants");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can create tenants"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot create tenants")
    void testSupportStaffCannotCreateTenant() {
        User supportStaff = createMockUser(User.Role.SUPPORT_STAFF, "support@example.com");
        mockCurrentUser(supportStaff);

        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("new-tenant");
        request.setName("New Tenant");
        request.setIsActive(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.createClient(request),
            "SUPPORT_STAFF should not be able to create tenants");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can create tenants"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    @Test
    @DisplayName("STUDENT cannot create tenants")
    void testStudentCannotCreateTenant() {
        User student = createMockUser(User.Role.STUDENT, "student@example.com");
        mockCurrentUser(student);

        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("new-tenant");
        request.setName("New Tenant");
        request.setIsActive(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.createClient(request),
            "STUDENT should not be able to create tenants");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can create tenants"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    @Test
    @DisplayName("Unauthenticated user cannot create tenants")
    void testUnauthenticatedUserCannotCreateTenant() {
        lenient().when(authentication.getName()).thenReturn(null);
        lenient().when(authentication.getPrincipal()).thenReturn(null);

        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("new-tenant");
        request.setName("New Tenant");
        request.setIsActive(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.createClient(request),
            "Unauthenticated user should not be able to create tenants");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can create tenants"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    // ========== updateClient() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can update tenants")
    void testSystemAdminCanUpdateTenant() {
        TenantContext.setClientId("SYSTEM");
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        UUID clientId = UUID.randomUUID();
        Client client = createMockClient(clientId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("updated-tenant");
        request.setName("Updated Tenant Name");
        request.setIsActive(true);

        assertDoesNotThrow(() -> clientService.updateClient(clientId, request),
            "SYSTEM_ADMIN should be able to update tenants");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot update tenants")
    void testTenantAdminCannotUpdateTenant() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        UUID clientId = UUID.randomUUID();
        CreateClientRequest request = new CreateClientRequest();
        request.setSlug("updated-tenant");
        request.setName("Updated Tenant Name");
        request.setIsActive(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.updateClient(clientId, request),
            "TENANT_ADMIN should not be able to update tenants");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can update tenants"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    // ========== deleteClient() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can delete tenants")
    void testSystemAdminCanDeleteTenant() {
        TenantContext.setClientId("SYSTEM");
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        UUID clientId = UUID.randomUUID();
        Client client = createMockClient(clientId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> clientService.deleteClient(clientId),
            "SYSTEM_ADMIN should be able to delete tenants");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot delete tenants")
    void testTenantAdminCannotDeleteTenant() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        UUID clientId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.deleteClient(clientId),
            "TENANT_ADMIN should not be able to delete tenants");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can delete tenants"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    // ========== getAllClients() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can see all tenants")
    void testSystemAdminCanSeeAllTenants() {
        TenantContext.setClientId("SYSTEM");
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        Client client1 = createMockClient(UUID.randomUUID());
        Client client2 = createMockClient(UUID.randomUUID());
        when(clientRepository.findAllByOrderByName()).thenReturn(List.of(client1, client2));

        List<?> clients = clientService.getAllClients();
        assertNotNull(clients, "SYSTEM_ADMIN should be able to retrieve all tenants");
        assertEquals(2, clients.size(), "Should return all tenants");
    }

    @Test
    @DisplayName("TENANT_ADMIN can only see their own tenant")
    void testTenantAdminCanOnlySeeOwnTenant() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        Client ownClient = createMockClient(testClientId);
        when(clientRepository.findById(testClientId)).thenReturn(Optional.of(ownClient));

        List<?> clients = clientService.getAllClients();
        assertNotNull(clients, "TENANT_ADMIN should be able to retrieve their tenant");
        assertEquals(1, clients.size(), "Should return only their tenant");
    }

    @Test
    @DisplayName("CONTENT_MANAGER can only see their own tenant")
    void testContentManagerCanOnlySeeOwnTenant() {
        User contentManager = createMockUser(User.Role.CONTENT_MANAGER, "content-manager@example.com");
        mockCurrentUser(contentManager);

        Client ownClient = createMockClient(testClientId);
        when(clientRepository.findById(testClientId)).thenReturn(Optional.of(ownClient));

        List<?> clients = clientService.getAllClients();
        assertNotNull(clients, "CONTENT_MANAGER should be able to retrieve their tenant");
        assertEquals(1, clients.size(), "Should return only their tenant");
    }

    // ========== getClientById() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can access any tenant by ID")
    void testSystemAdminCanAccessAnyTenantById() {
        TenantContext.setClientId("SYSTEM");
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        UUID clientId = UUID.randomUUID();
        Client client = createMockClient(clientId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        assertDoesNotThrow(() -> clientService.getClientById(clientId),
            "SYSTEM_ADMIN should be able to access any tenant by ID");
    }

    @Test
    @DisplayName("TENANT_ADMIN can only access their own tenant by ID")
    void testTenantAdminCanOnlyAccessOwnTenantById() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        UUID ownClientId = testClientId;
        Client ownClient = createMockClient(ownClientId);
        when(clientRepository.findById(ownClientId)).thenReturn(Optional.of(ownClient));

        assertDoesNotThrow(() -> clientService.getClientById(ownClientId),
            "TENANT_ADMIN should be able to access their own tenant by ID");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot access other tenant by ID")
    void testTenantAdminCannotAccessOtherTenantById() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        UUID otherClientId = UUID.randomUUID();
        Client otherClient = createMockClient(otherClientId);
        when(clientRepository.findById(otherClientId)).thenReturn(Optional.of(otherClient));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> clientService.getClientById(otherClientId),
            "TENANT_ADMIN should not be able to access other tenant by ID");

        assertTrue(exception.getMessage().contains("Client not found"),
            "Exception message should indicate client not found (access denied)");
    }
}
