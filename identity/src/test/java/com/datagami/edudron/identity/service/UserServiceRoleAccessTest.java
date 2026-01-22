package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.identity.domain.User;
import com.datagami.edudron.identity.dto.CreateUserRequest;
import com.datagami.edudron.identity.dto.UpdateUserRequest;
import com.datagami.edudron.identity.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        when(securityContext.getAuthentication()).thenReturn(authentication);
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
        when(authentication.getName()).thenReturn(user.getEmail());
        when(userRepository.findByEmailAndClientIdAndActiveTrue(user.getEmail(), testClientId))
            .thenReturn(Optional.of(user));
    }

    // ========== createUser() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can create SYSTEM_ADMIN user")
    void testSystemAdminCanCreateSystemAdmin() {
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        mockCurrentUser(systemAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new-admin@example.com");
        request.setPassword("password123");
        request.setName("New Admin");
        request.setRole("SYSTEM_ADMIN");

        when(userRepository.findByEmailAndActiveTrue(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.createUser(request),
            "SYSTEM_ADMIN should be able to create SYSTEM_ADMIN users");
    }

    @Test
    @DisplayName("SYSTEM_ADMIN can create TENANT_ADMIN user")
    void testSystemAdminCanCreateTenantAdmin() {
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        mockCurrentUser(systemAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("tenant-admin@example.com");
        request.setPassword("password123");
        request.setName("Tenant Admin");
        request.setRole("TENANT_ADMIN");

        when(userRepository.findByEmailAndClientIdAndActiveTrue(anyString(), any(UUID.class)))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.createUser(request),
            "SYSTEM_ADMIN should be able to create TENANT_ADMIN users");
    }

    @Test
    @DisplayName("SYSTEM_ADMIN can create CONTENT_MANAGER user")
    void testSystemAdminCanCreateContentManager() {
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        mockCurrentUser(systemAdmin);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("content-manager@example.com");
        request.setPassword("password123");
        request.setName("Content Manager");
        request.setRole("CONTENT_MANAGER");

        when(userRepository.findByEmailAndClientIdAndActiveTrue(anyString(), any(UUID.class)))
            .thenReturn(Optional.empty());
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

        when(userRepository.findByEmailAndClientIdAndActiveTrue(anyString(), any(UUID.class)))
            .thenReturn(Optional.empty());
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

        when(userRepository.findByEmailAndClientIdAndActiveTrue(anyString(), any(UUID.class)))
            .thenReturn(Optional.empty());
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

        when(userRepository.findByEmailAndClientIdAndActiveTrue(anyString(), any(UUID.class)))
            .thenReturn(Optional.empty());
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
        when(authentication.getName()).thenReturn(null);
        when(userRepository.findByEmailAndClientIdAndActiveTrue(anyString(), any(UUID.class)))
            .thenReturn(Optional.empty());

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
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        mockCurrentUser(systemAdmin);

        User targetUser = createMockUser(User.Role.STUDENT, "student@example.com");
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole("TENANT_ADMIN");

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
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
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
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
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
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole("INSTRUCTOR");

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
        when(userRepository.findById("target-user-id")).thenReturn(Optional.of(targetUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Updated Name");

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
        User systemAdmin = createMockUser(User.Role.SYSTEM_ADMIN, "admin@example.com");
        mockCurrentUser(systemAdmin);

        assertDoesNotThrow(() -> userService.assignInstitutesToUser("user-id", List.of("institute-1")),
            "SYSTEM_ADMIN should be able to assign institutes to users");
    }

    @Test
    @DisplayName("TENANT_ADMIN can assign institutes to users")
    void testTenantAdminCanAssignInstitutes() {
        User tenantAdmin = createMockUser(User.Role.TENANT_ADMIN, "tenant-admin@example.com");
        mockCurrentUser(tenantAdmin);

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
