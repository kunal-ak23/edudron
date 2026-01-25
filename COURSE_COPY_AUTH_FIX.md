# Course Copy Authorization Fix

## Issue

When a SYSTEM_ADMIN user tried to copy a course, they received this error:

```
Failed to submit copy job
Only SYSTEM_ADMIN can copy courses across tenants. Please ensure you're logged in as SYSTEM_ADMIN.
```

Even though they were logged in as SYSTEM_ADMIN.

## Root Cause

The `validateSystemAdmin()` method in `CourseCopyWorker.java` was checking the **tenant context** (`TenantContext.getClientId()`) instead of the **user's actual role**.

**Original incorrect code**:
```java
private void validateSystemAdmin() {
    String currentTenantContext = TenantContext.getClientId();
    
    if (currentTenantContext == null || 
        (!currentTenantContext.equals("SYSTEM") && 
         !currentTenantContext.equals("PENDING_TENANT_SELECTION"))) {
        throw new AccessDeniedException("Only SYSTEM_ADMIN can copy courses...");
    }
}
```

**Problem**: Tenant context is not the same as user role. A SYSTEM_ADMIN user might have a specific tenant selected in their context, but they still have SYSTEM_ADMIN role.

## Fix Applied

Updated `validateSystemAdmin()` to call the identity service and check the user's actual role (following the same pattern as `CourseService`):

**New correct code**:
```java
private void validateSystemAdmin() {
    String userRole = getCurrentUserRole();
    
    if (!"SYSTEM_ADMIN".equals(userRole)) {
        throw new AccessDeniedException("Only SYSTEM_ADMIN can copy courses across tenants. Current role: " + userRole);
    }
}

private String getCurrentUserRole() {
    // Call identity service to get user info
    ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
        gatewayUrl + "/idp/users/me",
        HttpMethod.GET,
        entity,
        new ParameterizedTypeReference<Map<String, Object>>() {}
    );
    
    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        Object role = response.getBody().get("role");
        return role != null ? role.toString() : null;
    }
    return null;
}
```

## Changes Made

**File**: `content/src/main/java/com/datagami/edudron/content/service/CourseCopyWorker.java`

1. ✅ Added imports for RestTemplate, HttpHeaders, etc.
2. ✅ Added `@Value` for gateway URL
3. ✅ Created `getRestTemplate()` method with proper interceptors
4. ✅ Created `getCurrentUserRole()` method to call identity service
5. ✅ Updated `validateSystemAdmin()` to check actual role instead of tenant context
6. ✅ Removed unused `TenantContext` import

## Verification

### Build Status
```bash
✅ BUILD SUCCESSFUL
✅ No compilation errors
✅ No linter errors
```

### How to Test

1. **Login as SYSTEM_ADMIN**
   - Your JWT token should have role: "SYSTEM_ADMIN"
   - Verify by calling: `GET /idp/users/me`

2. **Try Course Copy**
   - Go to `/super-admin/course-copy` in admin dashboard
   - Select source and target tenants
   - Click "Copy to Target" on any course
   - Should now work without authorization error

3. **Expected Behavior**
   - ✅ SYSTEM_ADMIN users can submit copy jobs
   - ✅ Non-SYSTEM_ADMIN users get clear error message
   - ✅ Error message shows their actual role

## Debug Tips

If you still get authorization errors:

1. **Check your JWT token**:
```bash
# Decode JWT to see role
curl "http://localhost:8080/idp/users/me" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

2. **Check backend logs**:
```
# Should see:
INFO  CourseCopyWorker - SYSTEM_ADMIN validation passed for user with role: SYSTEM_ADMIN

# Or if failing:
WARN  CourseCopyWorker - User with role TENANT_ADMIN attempted to copy course - access denied
```

3. **Verify gateway URL**:
```bash
# In application.yml, ensure:
GATEWAY_URL: http://localhost:8080
# Or set environment variable
```

## Related Files

- `CourseCopyWorker.java` - Fixed authorization check
- `CourseService.java` - Reference implementation for role checking
- `COURSE_COPY_QUICK_START.md` - Usage guide
- `COURSE_COPY_TESTING_GUIDE.md` - Testing guide

---

**Fixed**: January 26, 2026  
**Issue**: Authorization validation using wrong check  
**Resolution**: Changed from tenant context check to actual user role check via identity service
