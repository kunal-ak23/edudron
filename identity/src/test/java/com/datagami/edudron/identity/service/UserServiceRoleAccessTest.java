package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.identity.domain.User;
import com.datagami.edudron.identity.dto.CreateUserRequest;
import com.datagami.edudron.identity.dto.UpdateUserRequest;
import com.datagami.edudron.identity.repo.UserRepository;
import com.datagami.edudron.identity.repo.UserInstituteRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Test cases for UserService role-based access control.
 * Covers all scenarios for user creation, update, and institute assignment restrictions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Role-Based Access Control Tests")
class UserServiceRoleAccessTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInstituteRepository userInstituteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private UserService userService;

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

    private void mockCurrentUser(User user) {
        currentUser = user;
        lenient().when(authentication.getName()).thenReturn(user.getEmail());
        lenient().when(authentication.getPrincipal()).thenReturn(user.getEmail());
        
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

    // ========== createUser() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can create SYSTEM_ADMIN user")
    void testSystemAdminCanCreateSystemAdmin() {
        TenantContext.setClientId("SYSTEM");
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-admin@example.com");
        request.setPassword("password123");
        request.setName("New Admin");
        request.setRole("SYSTEM_ADMIN");

        // Mock for checking if new user exists (should return empty for new user's email)
        // This needs to handle both the initial check and potential retry logic
        lenient().when(userRepository.findByEmailAndRoleAndActiveTrue(eq("new-admin@example.com"), eq(User.Role.SYSTEM_ADMIN)))
            .thenReturn(Optional.empty());
        // Also mock with anyString() to handle any other email checks
        lenient().when(userRepository.findByEmailAndRoleAndActiveTrue(anyString(), eq(User.Role.SYSTEM_ADMIN)))
            .thenAnswer(invocation -> {
                String email = invocation.getArgument(0);
                // Return the current user if it's their email, otherwise empty
                if ("admin@example.com".equals(email)) {
                    return Optional.of(systemAdmin);
                }
                return Optional.empty();
            });
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.createUser(request),
            "SYSTEM_ADMIN should be able to create SYSTEM_ADMIN users");
    }

    @Test
    @DisplayName("SYSTEM_ADMIN can create TENANT_ADMIN user")
    void testSystemAdminCanCreateTenantAdmin() {
        TenantContext.setClientId(testClientId.toString());
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("tenant-admin@example.com");
        request.setPassword("password123");
        request.setName("Tenant Admin");
        request.setRole("TENANT_ADMIN");
        request.setInstituteIds(List.of("institute-1"));

        when(userRepository.existsByEmailAndClientId(anyString(), any(UUID.class))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.createUser(request),
            "SYSTEM_ADMIN should be able to create TENANT_ADMIN users");
    }

    @Test
    @DisplayName("SYSTEM_ADMIN can create CONTENT_MANAGER user")
    void testSystemAdminCanCreateContentManager() {
        TenantContext.setClientId(testClientId.toString());
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("content-manager@example.com");
        request.setPassword("password123");
        request.setName("Content Manager");
        request.setRole("CONTENT_MANAGER");
        request.setInstituteIds(List.of("institute-1"));

        when(userRepository.existsByEmailAndClientId(anyString(), any(UUID.class))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.createUser(request),
            "SYSTEM_ADMIN should be able to create CONTENT_MANAGER users");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot create SYSTEM_ADMIN user")
    void testTenantAdminCannotCreateSystemAdmin() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-admin@example.com");
        request.setPassword("password123");
        request.setName("New Admin");
        request.setRole("SYSTEM_ADMIN");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.createUser(request),
            "TENANT_ADMIN should not be able to create SYSTEM_ADMIN users");

        assertTrue(exception.getMessage().contains("SYSTEM_ADMIN users can only be created"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot create TENANT_ADMIN user")
    void testTenantAdminCannotCreateTenantAdmin() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-tenant-admin@example.com");
        request.setPassword("password123");
        request.setName("New Tenant Admin");
        request.setRole("TENANT_ADMIN");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.createUser(request),
            "TENANT_ADMIN should not be able to create TENANT_ADMIN users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can create platform-side roles"),
            "Exception message should indicate platform-side role restriction");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot create CONTENT_MANAGER user")
    void testTenantAdminCannotCreateContentManager() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("content-manager@example.com");
        request.setPassword("password123");
        request.setName("Content Manager");
        request.setRole("CONTENT_MANAGER");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.createUser(request),
            "TENANT_ADMIN should not be able to create CONTENT_MANAGER users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can create platform-side roles"),
            "Exception message should indicate platform-side role restriction");
    }

    @Test
    @DisplayName("TENANT_ADMIN can create INSTRUCTOR user")
    void testTenantAdminCanCreateInstructor() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("instructor@example.com");
        request.setPassword("password123");
        request.setName("Instructor");
        request.setRole("INSTRUCTOR");
        request.setInstituteIds(List.of("institute-1"));

        when(userRepository.existsByEmailAndClientId(anyString(), any(UUID.class))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.createUser(request),
            "TENANT_ADMIN should be able to create INSTRUCTOR users");
    }

    @Test
    @DisplayName("TENANT_ADMIN can create SUPPORT_STAFF user")
    void testTenantAdminCanCreateSupportStaff() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("support@example.com");
        request.setPassword("password123");
        request.setName("Support Staff");
        request.setRole("SUPPORT_STAFF");
        request.setInstituteIds(List.of("institute-1"));

        when(userRepository.existsByEmailAndClientId(anyString(), any(UUID.class))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.createUser(request),
            "TENANT_ADMIN should be able to create SUPPORT_STAFF users");
    }

    @Test
    @DisplayName("TENANT_ADMIN can create STUDENT user")
    void testTenantAdminCanCreateStudent() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("student@example.com");
        request.setPassword("password123");
        request.setName("Student");
        request.setRole("STUDENT");
        request.setInstituteIds(List.of("institute-1"));

        when(userRepository.existsByEmailAndClientId(anyString(), any(UUID.class))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.createUser(request),
            "TENANT_ADMIN should be able to create STUDENT users");
    }

    @Test
    @DisplayName("CONTENT_MANAGER cannot create users")
    void testContentManagerCannotCreateUsers() {
        User contentManager = createMockUser(User.Role.CONTENT_MANAGER, "content-manager@example.com");
        mockCurrentUser(contentManager);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-user@example.com");
        request.setPassword("password123");
        request.setName("New User");
        request.setRole("STUDENT");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.createUser(request),
            "CONTENT_MANAGER should not be able to create users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN and TENANT_ADMIN can create users"),
            "Exception message should indicate user management restriction");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot create users")
    void testInstructorCannotCreateUsers() {
        User instructor = createMockUser(User.Role.INSTRUCTOR, "instructor@example.com");
        mockCurrentUser(instructor);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-user@example.com");
        request.setPassword("password123");
        request.setName("New User");
        request.setRole("STUDENT");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.createUser(request),
            "INSTRUCTOR should not be able to create users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN and TENANT_ADMIN can create users"),
            "Exception message should indicate user management restriction");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot create users")
    void testSupportStaffCannotCreateUsers() {
        User supportStaff = createMockUser(User.Role.SUPPORT_STAFF, "support@example.com");
        mockCurrentUser(supportStaff);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-user@example.com");
        request.setPassword("password123");
        request.setName("New User");
        request.setRole("STUDENT");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.createUser(request),
            "SUPPORT_STAFF should not be able to create users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN and TENANT_ADMIN can create users"),
            "Exception message should indicate user management restriction");
    }

    @Test
    @DisplayName("STUDENT cannot create users")
    void testStudentCannotCreateUsers() {
        User student = createMockUser(User.Role.STUDENT, "student@example.com");
        mockCurrentUser(student);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-user@example.com");
        request.setPassword("password123");
        request.setName("New User");
        request.setRole("STUDENT");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.createUser(request),
            "STUDENT should not be able to create users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN and TENANT_ADMIN can create users"),
            "Exception message should indicate user management restriction");
    }

    @Test
    @DisplayName("Unauthenticated user cannot create users")
    void testUnauthenticatedUserCannotCreateUsers() {
        lenient().when(authentication.getName()).thenReturn(null);
        lenient().when(authentication.getPrincipal()).thenReturn(null);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-user@example.com");
        request.setPassword("password123");
        request.setName("New User");
        request.setRole("STUDENT");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.createUser(request),
            "Unauthenticated user should not be able to create users");

        assertTrue(exception.getMessage().contains("Authentication required"),
            "Exception message should indicate authentication requirement");
    }

    // ========== updateUser() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can update any user role")
    void testSystemAdminCanUpdateAnyUserRole() {
        TenantContext.setClientId(testClientId.toString());
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        User targetUser = createMockUser(User.Role.STUDENT, "student@example.com");
        targetUser.setClientId(testClientId); // Target user in tenant
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail(targetUser.getEmail()); // Keep same email to avoid uniqueness check
        request.setName(targetUser.getName());
        request.setRole("TENANT_ADMIN");
        request.setInstituteIds(List.of("institute-1"));

        // Mock email uniqueness check (email not changed, so this won't be called, but mock it anyway)
        lenient().when(userRepository.existsByEmailAndClientId(anyString(), any(UUID.class))).thenReturn(false);
        lenient().when(userRepository.findByEmailAndClientId(anyString(), any(UUID.class)))
            .thenReturn(Optional.empty());
        doNothing().when(userInstituteRepository).deleteByUserId(anyString());
        when(userInstituteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.updateUser("target-user-id", request),
            "SYSTEM_ADMIN should be able to update any user role");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot update user to SYSTEM_ADMIN")
    void testTenantAdminCannotUpdateToSystemAdmin() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        User targetUser = createMockUser(User.Role.STUDENT, "student@example.com");
        targetUser.setClientId(testClientId); // Must be in same tenant
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail(targetUser.getEmail());
        request.setName(targetUser.getName());
        request.setRole("SYSTEM_ADMIN");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.updateUser("target-user-id", request),
            "TENANT_ADMIN should not be able to update user to SYSTEM_ADMIN");

        assertTrue(exception.getMessage().contains("SYSTEM_ADMIN role can only be assigned"),
            "Exception message should indicate SYSTEM_ADMIN restriction");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot update user to platform-side roles")
    void testTenantAdminCannotUpdateToPlatformSideRoles() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        User targetUser = createMockUser(User.Role.STUDENT, "student@example.com");
        targetUser.setClientId(testClientId); // Must be in same tenant
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail(targetUser.getEmail());
        request.setName(targetUser.getName());
        request.setRole("CONTENT_MANAGER");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.updateUser("target-user-id", request),
            "TENANT_ADMIN should not be able to update user to CONTENT_MANAGER");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN can assign platform-side roles"),
            "Exception message should indicate platform-side role restriction");
    }

    @Test
    @DisplayName("TENANT_ADMIN can update user to university-side roles")
    void testTenantAdminCanUpdateToUniversitySideRoles() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        User targetUser = createMockUser(User.Role.STUDENT, "student@example.com");
        // Target user must be in same tenant
        targetUser.setClientId(testClientId);
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail(targetUser.getEmail()); // Keep same email to avoid uniqueness check
        request.setName(targetUser.getName());
        request.setRole("INSTRUCTOR");
        request.setInstituteIds(List.of("institute-1"));

        // Mock email uniqueness check (email not changed, so this won't be called, but mock it anyway)
        // Note: We don't mock findByEmailAndClientId with anyString() here because it would override
        // the specific mock from mockCurrentUser() that's needed for getCurrentUser() to work in assignInstitutesToUser
        lenient().when(userRepository.existsByEmailAndClientId(anyString(), any(UUID.class))).thenReturn(false);
        doNothing().when(userInstituteRepository).deleteByUserId(anyString());
        when(userInstituteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.updateUser("target-user-id", request),
            "TENANT_ADMIN should be able to update user to INSTRUCTOR");
    }

    @Test
    @DisplayName("CONTENT_MANAGER cannot update users")
    void testContentManagerCannotUpdateUsers() {
        User contentManager = createMockUser(User.Role.CONTENT_MANAGER, "content-manager@example.com");
        mockCurrentUser(contentManager);

        User targetUser = createMockUser(User.Role.STUDENT, "student@example.com");
        targetUser.setClientId(testClientId); // Must be in same tenant
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail(targetUser.getEmail());
        request.setName("Updated Name");
        request.setRole(targetUser.getRole().name()); // Keep same role

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.updateUser("target-user-id", request),
            "CONTENT_MANAGER should not be able to update users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN and TENANT_ADMIN can update users"),
            "Exception message should indicate user management restriction");
    }

    // ========== assignInstitutesToUser() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can assign institutes to users")
    void testSystemAdminCanAssignInstitutes() {
        TenantContext.setClientId("SYSTEM");
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        systemAdmin.setClientId(null);
        mockCurrentUser(systemAdmin);

        doNothing().when(userInstituteRepository).deleteByUserId(anyString());
        when(userInstituteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.assignInstitutesToUser("user-id", List.of("institute-1")),
            "SYSTEM_ADMIN should be able to assign institutes to users");
    }

    @Test
    @DisplayName("TENANT_ADMIN can assign institutes to users")
    void testTenantAdminCanAssignInstitutes() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

        doNothing().when(userInstituteRepository).deleteByUserId(anyString());
        when(userInstituteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.assignInstitutesToUser("user-id", List.of("institute-1")),
            "TENANT_ADMIN should be able to assign institutes to users");
    }

    @Test
    @DisplayName("CONTENT_MANAGER cannot assign institutes to users")
    void testContentManagerCannotAssignInstitutes() {
        User contentManager = createMockUser(User.Role.CONTENT_MANAGER, "content-manager@example.com");
        mockCurrentUser(contentManager);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.assignInstitutesToUser("user-id", List.of("institute-1")),
            "CONTENT_MANAGER should not be able to assign institutes to users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN and TENANT_ADMIN can modify institute associations"),
            "Exception message should indicate institute association restriction");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot assign institutes to users")
    void testInstructorCannotAssignInstitutes() {
        User instructor = createMockUser(User.Role.INSTRUCTOR, "instructor@example.com");
        mockCurrentUser(instructor);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> userService.assignInstitutesToUser("user-id", List.of("institute-1")),
            "INSTRUCTOR should not be able to assign institutes to users");

        assertTrue(exception.getMessage().contains("Only SYSTEM_ADMIN and TENANT_ADMIN can modify institute associations"),
            "Exception message should indicate institute association restriction");
    }
}
