# Role-Based Access Control Test Cases

This document provides a comprehensive overview of all test cases created for role-based access control implementation.

## Test Files Created

1. **UserDomainRolePermissionTest.java** - Tests User domain class permission helper methods
2. **UserServiceRoleAccessTest.java** - Tests UserService role-based access control
3. **ClientServiceRoleAccessTest.java** - Tests ClientService (tenant management) role-based access control
4. **CourseServiceRoleAccessTest.java** - Tests CourseService role-based access control

## Test Coverage Summary

### 1. User Domain Permission Tests (UserDomainRolePermissionTest)

#### canManageContent() Tests
- ✅ SYSTEM_ADMIN can manage content
- ✅ TENANT_ADMIN can manage content
- ✅ CONTENT_MANAGER can manage content
- ❌ INSTRUCTOR cannot manage content (view-only)
- ❌ SUPPORT_STAFF cannot manage content (view-only)
- ❌ STUDENT cannot manage content

#### canManageUsers() Tests
- ✅ SYSTEM_ADMIN can manage users
- ✅ TENANT_ADMIN can manage users
- ❌ CONTENT_MANAGER cannot manage users
- ❌ INSTRUCTOR cannot manage users
- ❌ SUPPORT_STAFF cannot manage users
- ❌ STUDENT cannot manage users

#### canViewAnalytics() Tests
- ✅ SYSTEM_ADMIN can view analytics
- ✅ TENANT_ADMIN can view analytics
- ✅ CONTENT_MANAGER can view analytics
- ✅ INSTRUCTOR can view analytics
- ✅ SUPPORT_STAFF can view analytics
- ❌ STUDENT cannot view analytics

#### canAccessCourses() Tests
- ✅ SYSTEM_ADMIN can access courses
- ✅ TENANT_ADMIN can access courses
- ✅ CONTENT_MANAGER can access courses
- ✅ INSTRUCTOR can access courses
- ✅ SUPPORT_STAFF can access courses
- ✅ STUDENT can access courses

#### canViewOnly() Tests
- ✅ INSTRUCTOR has view-only access
- ✅ SUPPORT_STAFF has view-only access
- ❌ SYSTEM_ADMIN does not have view-only access
- ❌ TENANT_ADMIN does not have view-only access
- ❌ CONTENT_MANAGER does not have view-only access
- ❌ STUDENT does not have view-only access

#### canViewStudentProgress() Tests
- ✅ SYSTEM_ADMIN can view student progress
- ✅ TENANT_ADMIN can view student progress
- ✅ INSTRUCTOR can view student progress
- ❌ SUPPORT_STAFF cannot view student progress
- ❌ CONTENT_MANAGER cannot view student progress
- ❌ STUDENT cannot view student progress

### 2. UserService Role Access Tests (UserServiceRoleAccessTest)

#### User Creation Tests
- ✅ SYSTEM_ADMIN can create SYSTEM_ADMIN user
- ✅ SYSTEM_ADMIN can create TENANT_ADMIN user
- ✅ SYSTEM_ADMIN can create CONTENT_MANAGER user
- ❌ TENANT_ADMIN cannot create SYSTEM_ADMIN user
- ❌ TENANT_ADMIN cannot create TENANT_ADMIN user
- ❌ TENANT_ADMIN cannot create CONTENT_MANAGER user
- ✅ TENANT_ADMIN can create INSTRUCTOR user
- ✅ TENANT_ADMIN can create SUPPORT_STAFF user
- ✅ TENANT_ADMIN can create STUDENT user
- ❌ CONTENT_MANAGER cannot create users
- ❌ INSTRUCTOR cannot create users
- ❌ SUPPORT_STAFF cannot create users
- ❌ STUDENT cannot create users
- ❌ Unauthenticated user cannot create users

#### User Update Tests
- ✅ SYSTEM_ADMIN can update any user role
- ❌ TENANT_ADMIN cannot update user to SYSTEM_ADMIN
- ❌ TENANT_ADMIN cannot update user to platform-side roles (CONTENT_MANAGER)
- ✅ TENANT_ADMIN can update user to university-side roles (INSTRUCTOR)
- ❌ CONTENT_MANAGER cannot update users

#### Institute Assignment Tests
- ✅ SYSTEM_ADMIN can assign institutes to users
- ✅ TENANT_ADMIN can assign institutes to users
- ❌ CONTENT_MANAGER cannot assign institutes to users
- ❌ INSTRUCTOR cannot assign institutes to users

### 3. ClientService Role Access Tests (ClientServiceRoleAccessTest)

#### Tenant Creation Tests
- ✅ SYSTEM_ADMIN can create tenants
- ❌ TENANT_ADMIN cannot create tenants
- ❌ CONTENT_MANAGER cannot create tenants
- ❌ INSTRUCTOR cannot create tenants
- ❌ SUPPORT_STAFF cannot create tenants
- ❌ STUDENT cannot create tenants
- ❌ Unauthenticated user cannot create tenants

#### Tenant Update Tests
- ✅ SYSTEM_ADMIN can update tenants
- ❌ TENANT_ADMIN cannot update tenants

#### Tenant Deletion Tests
- ✅ SYSTEM_ADMIN can delete tenants
- ❌ TENANT_ADMIN cannot delete tenants

#### Tenant Viewing Tests
- ✅ SYSTEM_ADMIN can see all tenants
- ✅ TENANT_ADMIN can only see their own tenant
- ✅ CONTENT_MANAGER can only see their own tenant

#### Tenant Access by ID Tests
- ✅ SYSTEM_ADMIN can access any tenant by ID
- ✅ TENANT_ADMIN can only access their own tenant by ID
- ❌ TENANT_ADMIN cannot access other tenant by ID

### 4. CourseService Role Access Tests (CourseServiceRoleAccessTest)

#### Course Creation Tests
- ✅ SYSTEM_ADMIN can create courses
- ✅ TENANT_ADMIN can create courses
- ✅ CONTENT_MANAGER can create courses
- ❌ INSTRUCTOR cannot create courses (view-only)
- ❌ SUPPORT_STAFF cannot create courses (view-only)
- ❌ STUDENT cannot create courses (view-only)

#### Course Update Tests
- ✅ SYSTEM_ADMIN can update courses
- ✅ TENANT_ADMIN can update courses
- ✅ CONTENT_MANAGER can update courses
- ❌ INSTRUCTOR cannot update courses (view-only)
- ❌ SUPPORT_STAFF cannot update courses (view-only)

#### Course Deletion Tests
- ✅ SYSTEM_ADMIN can delete courses
- ✅ TENANT_ADMIN can delete courses
- ✅ CONTENT_MANAGER can delete courses
- ❌ INSTRUCTOR cannot delete courses (view-only)
- ❌ SUPPORT_STAFF cannot delete courses (view-only)

#### Course Publishing Tests
- ✅ SYSTEM_ADMIN can publish courses
- ✅ TENANT_ADMIN can publish courses
- ✅ CONTENT_MANAGER can publish courses
- ❌ INSTRUCTOR cannot publish courses (view-only)
- ❌ SUPPORT_STAFF cannot publish courses (view-only)

#### Course Unpublishing Tests
- ✅ SYSTEM_ADMIN can unpublish courses
- ❌ INSTRUCTOR cannot unpublish courses (view-only)
- ❌ SUPPORT_STAFF cannot unpublish courses (view-only)

## Role Summary Matrix

| Action | SYSTEM_ADMIN | TENANT_ADMIN | CONTENT_MANAGER | INSTRUCTOR | SUPPORT_STAFF | STUDENT |
|--------|--------------|--------------|-----------------|------------|---------------|---------|
| **User Management** |
| Create SYSTEM_ADMIN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Create TENANT_ADMIN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Create CONTENT_MANAGER | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Create INSTRUCTOR | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Create SUPPORT_STAFF | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Create STUDENT | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Update Users | ✅ | ✅* | ❌ | ❌ | ❌ | ❌ |
| Assign Institutes | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Tenant Management** |
| Create Tenant | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Update Tenant | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Delete Tenant | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| View All Tenants | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| View Own Tenant | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Course Management** |
| Create Course | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Update Course | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Delete Course | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Publish Course | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Unpublish Course | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| View Courses | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Analytics & Dashboard** |
| View Analytics | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| View Student Progress | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| View Dashboard | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |

*TENANT_ADMIN can only update users to university-side roles (INSTRUCTOR, SUPPORT_STAFF, STUDENT)

## Running the Tests

### Run all role access tests:
```bash
./gradlew test --tests "*RoleAccess*" --tests "*RolePermission*"
```

### Run specific test class:
```bash
./gradlew test --tests "com.datagami.edudron.identity.domain.UserDomainRolePermissionTest"
./gradlew test --tests "com.datagami.edudron.identity.service.UserServiceRoleAccessTest"
./gradlew test --tests "com.datagami.edudron.identity.service.ClientServiceRoleAccessTest"
./gradlew test --tests "com.datagami.edudron.content.service.CourseServiceRoleAccessTest"
```

## Test Statistics

- **Total Test Files**: 4
- **Total Test Methods**: ~100+
- **Coverage Areas**:
  - User domain permissions (6 permission methods × 6 roles = 36+ tests)
  - UserService operations (create, update, assign institutes)
  - ClientService operations (create, update, delete, view)
  - CourseService operations (create, update, delete, publish, unpublish)

## Notes

1. All tests use Mockito for mocking dependencies
2. Tests follow JUnit 5 conventions with `@DisplayName` annotations
3. SecurityContext and Authentication are mocked to simulate different user roles
4. TenantContext is properly set up and cleaned up in each test
5. Exception messages are verified to ensure proper error handling

## Future Test Additions

Consider adding:
- Integration tests for end-to-end role-based access flows
- Frontend component tests for UI restrictions
- API endpoint tests for controller-level restrictions
- Performance tests for role permission checks
