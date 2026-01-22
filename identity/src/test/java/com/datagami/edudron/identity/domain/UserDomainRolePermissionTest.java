package com.datagami.edudron.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for User domain class role-based permission helper methods.
 * Covers all role permission scenarios implemented in recent updates.
 */
@DisplayName("User Domain Role Permission Tests")
class UserDomainRolePermissionTest {

    private User createUser(User.Role role) {
        User user = new User();
        user.setId("test-user-id");
        user.setClientId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPassword("hashed-password");
        user.setName("Test User");
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    // ========== canManageContent() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can manage content")
    void testSystemAdminCanManageContent() {
        User user = createUser(User.Role.SYSTEM_ADMIN);
        assertTrue(user.canManageContent(), "SYSTEM_ADMIN should be able to manage content");
    }

    @Test
    @DisplayName("TENANT_ADMIN can manage content")
    void testTenantAdminCanManageContent() {
        User user = createUser(User.Role.TENANT_ADMIN);
        assertTrue(user.canManageContent(), "TENANT_ADMIN should be able to manage content");
    }

    @Test
    @DisplayName("CONTENT_MANAGER can manage content")
    void testContentManagerCanManageContent() {
        User user = createUser(User.Role.CONTENT_MANAGER);
        assertTrue(user.canManageContent(), "CONTENT_MANAGER should be able to manage content");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot manage content (view-only)")
    void testInstructorCannotManageContent() {
        User user = createUser(User.Role.INSTRUCTOR);
        assertFalse(user.canManageContent(), "INSTRUCTOR should NOT be able to manage content (view-only)");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot manage content (view-only)")
    void testSupportStaffCannotManageContent() {
        User user = createUser(User.Role.SUPPORT_STAFF);
        assertFalse(user.canManageContent(), "SUPPORT_STAFF should NOT be able to manage content (view-only)");
    }

    @Test
    @DisplayName("STUDENT cannot manage content")
    void testStudentCannotManageContent() {
        User user = createUser(User.Role.STUDENT);
        assertFalse(user.canManageContent(), "STUDENT should NOT be able to manage content");
    }

    // ========== canManageUsers() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can manage users")
    void testSystemAdminCanManageUsers() {
        User user = createUser(User.Role.SYSTEM_ADMIN);
        assertTrue(user.canManageUsers(), "SYSTEM_ADMIN should be able to manage users");
    }

    @Test
    @DisplayName("TENANT_ADMIN can manage users")
    void testTenantAdminCanManageUsers() {
        User user = createUser(User.Role.TENANT_ADMIN);
        assertTrue(user.canManageUsers(), "TENANT_ADMIN should be able to manage users");
    }

    @Test
    @DisplayName("CONTENT_MANAGER cannot manage users")
    void testContentManagerCannotManageUsers() {
        User user = createUser(User.Role.CONTENT_MANAGER);
        assertFalse(user.canManageUsers(), "CONTENT_MANAGER should NOT be able to manage users");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot manage users")
    void testInstructorCannotManageUsers() {
        User user = createUser(User.Role.INSTRUCTOR);
        assertFalse(user.canManageUsers(), "INSTRUCTOR should NOT be able to manage users");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot manage users")
    void testSupportStaffCannotManageUsers() {
        User user = createUser(User.Role.SUPPORT_STAFF);
        assertFalse(user.canManageUsers(), "SUPPORT_STAFF should NOT be able to manage users");
    }

    @Test
    @DisplayName("STUDENT cannot manage users")
    void testStudentCannotManageUsers() {
        User user = createUser(User.Role.STUDENT);
        assertFalse(user.canManageUsers(), "STUDENT should NOT be able to manage users");
    }

    // ========== canViewAnalytics() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can view analytics")
    void testSystemAdminCanViewAnalytics() {
        User user = createUser(User.Role.SYSTEM_ADMIN);
        assertTrue(user.canViewAnalytics(), "SYSTEM_ADMIN should be able to view analytics");
    }

    @Test
    @DisplayName("TENANT_ADMIN can view analytics")
    void testTenantAdminCanViewAnalytics() {
        User user = createUser(User.Role.TENANT_ADMIN);
        assertTrue(user.canViewAnalytics(), "TENANT_ADMIN should be able to view analytics");
    }

    @Test
    @DisplayName("CONTENT_MANAGER can view analytics")
    void testContentManagerCanViewAnalytics() {
        User user = createUser(User.Role.CONTENT_MANAGER);
        assertTrue(user.canViewAnalytics(), "CONTENT_MANAGER should be able to view analytics");
    }

    @Test
    @DisplayName("INSTRUCTOR can view analytics")
    void testInstructorCanViewAnalytics() {
        User user = createUser(User.Role.INSTRUCTOR);
        assertTrue(user.canViewAnalytics(), "INSTRUCTOR should be able to view analytics");
    }

    @Test
    @DisplayName("SUPPORT_STAFF can view analytics")
    void testSupportStaffCanViewAnalytics() {
        User user = createUser(User.Role.SUPPORT_STAFF);
        assertTrue(user.canViewAnalytics(), "SUPPORT_STAFF should be able to view analytics");
    }

    @Test
    @DisplayName("STUDENT cannot view analytics")
    void testStudentCannotViewAnalytics() {
        User user = createUser(User.Role.STUDENT);
        assertFalse(user.canViewAnalytics(), "STUDENT should NOT be able to view analytics");
    }

    // ========== canAccessCourses() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can access courses")
    void testSystemAdminCanAccessCourses() {
        User user = createUser(User.Role.SYSTEM_ADMIN);
        assertTrue(user.canAccessCourses(), "SYSTEM_ADMIN should be able to access courses");
    }

    @Test
    @DisplayName("TENANT_ADMIN can access courses")
    void testTenantAdminCanAccessCourses() {
        User user = createUser(User.Role.TENANT_ADMIN);
        assertTrue(user.canAccessCourses(), "TENANT_ADMIN should be able to access courses");
    }

    @Test
    @DisplayName("CONTENT_MANAGER can access courses")
    void testContentManagerCanAccessCourses() {
        User user = createUser(User.Role.CONTENT_MANAGER);
        assertTrue(user.canAccessCourses(), "CONTENT_MANAGER should be able to access courses");
    }

    @Test
    @DisplayName("INSTRUCTOR can access courses")
    void testInstructorCanAccessCourses() {
        User user = createUser(User.Role.INSTRUCTOR);
        assertTrue(user.canAccessCourses(), "INSTRUCTOR should be able to access courses");
    }

    @Test
    @DisplayName("SUPPORT_STAFF can access courses")
    void testSupportStaffCanAccessCourses() {
        User user = createUser(User.Role.SUPPORT_STAFF);
        assertTrue(user.canAccessCourses(), "SUPPORT_STAFF should be able to access courses");
    }

    @Test
    @DisplayName("STUDENT can access courses")
    void testStudentCanAccessCourses() {
        User user = createUser(User.Role.STUDENT);
        assertTrue(user.canAccessCourses(), "STUDENT should be able to access courses");
    }

    // ========== canViewOnly() Tests ==========

    @Test
    @DisplayName("INSTRUCTOR has view-only access")
    void testInstructorHasViewOnlyAccess() {
        User user = createUser(User.Role.INSTRUCTOR);
        assertTrue(user.canViewOnly(), "INSTRUCTOR should have view-only access");
    }

    @Test
    @DisplayName("SUPPORT_STAFF has view-only access")
    void testSupportStaffHasViewOnlyAccess() {
        User user = createUser(User.Role.SUPPORT_STAFF);
        assertTrue(user.canViewOnly(), "SUPPORT_STAFF should have view-only access");
    }

    @Test
    @DisplayName("SYSTEM_ADMIN does not have view-only access")
    void testSystemAdminDoesNotHaveViewOnlyAccess() {
        User user = createUser(User.Role.SYSTEM_ADMIN);
        assertFalse(user.canViewOnly(), "SYSTEM_ADMIN should NOT have view-only access");
    }

    @Test
    @DisplayName("TENANT_ADMIN does not have view-only access")
    void testTenantAdminDoesNotHaveViewOnlyAccess() {
        User user = createUser(User.Role.TENANT_ADMIN);
        assertFalse(user.canViewOnly(), "TENANT_ADMIN should NOT have view-only access");
    }

    @Test
    @DisplayName("CONTENT_MANAGER does not have view-only access")
    void testContentManagerDoesNotHaveViewOnlyAccess() {
        User user = createUser(User.Role.CONTENT_MANAGER);
        assertFalse(user.canViewOnly(), "CONTENT_MANAGER should NOT have view-only access");
    }

    @Test
    @DisplayName("STUDENT does not have view-only access")
    void testStudentDoesNotHaveViewOnlyAccess() {
        User user = createUser(User.Role.STUDENT);
        assertFalse(user.canViewOnly(), "STUDENT should NOT have view-only access");
    }

    // ========== canViewStudentProgress() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can view student progress")
    void testSystemAdminCanViewStudentProgress() {
        User user = createUser(User.Role.SYSTEM_ADMIN);
        assertTrue(user.canViewStudentProgress(), "SYSTEM_ADMIN should be able to view student progress");
    }

    @Test
    @DisplayName("TENANT_ADMIN can view student progress")
    void testTenantAdminCanViewStudentProgress() {
        User user = createUser(User.Role.TENANT_ADMIN);
        assertTrue(user.canViewStudentProgress(), "TENANT_ADMIN should be able to view student progress");
    }

    @Test
    @DisplayName("INSTRUCTOR can view student progress")
    void testInstructorCanViewStudentProgress() {
        User user = createUser(User.Role.INSTRUCTOR);
        assertTrue(user.canViewStudentProgress(), "INSTRUCTOR should be able to view student progress");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot view student progress")
    void testSupportStaffCannotViewStudentProgress() {
        User user = createUser(User.Role.SUPPORT_STAFF);
        assertFalse(user.canViewStudentProgress(), "SUPPORT_STAFF should NOT be able to view student progress");
    }

    @Test
    @DisplayName("CONTENT_MANAGER cannot view student progress")
    void testContentManagerCannotViewStudentProgress() {
        User user = createUser(User.Role.CONTENT_MANAGER);
        assertFalse(user.canViewStudentProgress(), "CONTENT_MANAGER should NOT be able to view student progress");
    }

    @Test
    @DisplayName("STUDENT cannot view student progress")
    void testStudentCannotViewStudentProgress() {
        User user = createUser(User.Role.STUDENT);
        assertFalse(user.canViewStudentProgress(), "STUDENT should NOT be able to view student progress");
    }

    // ========== Role-specific helper methods ==========

    @Test
    @DisplayName("isSystemAdmin returns true for SYSTEM_ADMIN")
    void testIsSystemAdmin() {
        User user = createUser(User.Role.SYSTEM_ADMIN);
        assertTrue(user.isSystemAdmin(), "isSystemAdmin should return true for SYSTEM_ADMIN");
    }

    @Test
    @DisplayName("isInstructor returns true for INSTRUCTOR")
    void testIsInstructor() {
        User user = createUser(User.Role.INSTRUCTOR);
        assertTrue(user.isInstructor(), "isInstructor should return true for INSTRUCTOR");
    }

    @Test
    @DisplayName("isStudent returns true for STUDENT")
    void testIsStudent() {
        User user = createUser(User.Role.STUDENT);
        assertTrue(user.isStudent(), "isStudent should return true for STUDENT");
    }

    @Test
    @DisplayName("hasAdminPrivileges returns true for SYSTEM_ADMIN and TENANT_ADMIN")
    void testHasAdminPrivileges() {
        User systemAdmin = createUser(User.Role.SYSTEM_ADMIN);
        User tenantAdmin = createUser(User.Role.TENANT_ADMIN);
        User contentManager = createUser(User.Role.CONTENT_MANAGER);

        assertTrue(systemAdmin.hasAdminPrivileges(), "SYSTEM_ADMIN should have admin privileges");
        assertTrue(tenantAdmin.hasAdminPrivileges(), "TENANT_ADMIN should have admin privileges");
        assertFalse(contentManager.hasAdminPrivileges(), "CONTENT_MANAGER should NOT have admin privileges");
    }
}
