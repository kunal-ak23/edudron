# Email Case Sensitivity Fix

## Problem Diagnosed

The bulk import "Update existing students" feature was failing with the error:
```
User already exists but could not be found for update
```

### Root Cause

The issue was **email case sensitivity mismatch** in database queries:

1. **Check for existing user**: `existsByEmailAndClientId("en22cs301959@medicaps.ac.in", clientId)` 
   - Returns `TRUE` if database has "EN22CS301959@MEDICAPS.AC.IN" (database collation is case-insensitive)

2. **Find user for update**: `findByEmailAndClientId("en22cs301959@medicaps.ac.in", clientId)`
   - Returns `EMPTY` because JPA's default string comparison is **case-sensitive**

This mismatch caused:
- User creation fails → "User already exists"
- Upsert tries to find user → Returns null
- Error: "User already exists but could not be found for update"

### Additional Issues Found

1. **URL encoding**: Double-encoding of email parameters in REST calls
2. **Authentication context**: The authenticated user lookup was also case-sensitive

## Solution

### 1. Added Case-Insensitive Repository Methods

**File**: `identity/src/main/java/com/datagami/edudron/identity/repo/UserRepository.java`

Added `IgnoreCase` versions of all email-based queries:
```java
// Case-insensitive methods (recommended for email lookups)
Optional<User> findByEmailIgnoreCaseAndClientId(String email, UUID clientId);
boolean existsByEmailIgnoreCaseAndClientId(String email, UUID clientId);
Optional<User> findByEmailIgnoreCaseAndRoleAndActiveTrue(String email, User.Role role);
// ... and more
```

### 2. Updated UserService to Use Case-Insensitive Queries

**File**: `identity/src/main/java/com/datagami/edudron/identity/service/UserService.java`

Updated all methods to use case-insensitive lookups:
- `getUserByEmail()` - Finding users by email
- `createUser()` - Checking for existing users
- `updateUser()` - Email uniqueness validation  
- `getCurrentUser()` - Authentication context lookup
- `createSystemAdminUser()` - SYSTEM_ADMIN checks

### 3. Fixed REST Template URL Encoding

**File**: `student/src/main/java/com/datagami/edudron/student/service/BulkStudentImportService.java`

Changed from manual URL encoding to URI template:
```java
// Before (double-encoding issue)
String url = gatewayUrl + "/idp/users/by-email?email=" + URLEncoder.encode(email);

// After (proper encoding)
String urlTemplate = gatewayUrl + "/idp/users/by-email?email={email}";
Map<String, String> uriVariables = Map.of("email", normalizedEmail);
response = restTemplate.exchange(urlTemplate, GET, entity, UserResponseDTO.class, uriVariables);
```

### 4. Added Comprehensive Logging

Added debug and info logging throughout:
- Request parameter logging in controllers
- Email normalization tracking
- Authorization header forwarding status
- Detailed error diagnostics

## Files Changed

### Identity Service
- `identity/src/main/java/com/datagami/edudron/identity/repo/UserRepository.java`
  - Added 6 new case-insensitive repository methods
  
- `identity/src/main/java/com/datagami/edudron/identity/service/UserService.java`
  - Updated `getUserByEmail()` to use case-insensitive search
  - Updated `createUser()` to use case-insensitive existence check
  - Updated `updateUser()` to use case-insensitive uniqueness validation
  - Updated `getCurrentUser()` to use case-insensitive authentication lookup
  - Updated `createSystemAdminUser()` and retry logic
  
- `identity/src/main/java/com/datagami/edudron/identity/web/UserController.java`
  - Added debug logging to `/by-email` endpoint

### Student Service
- `student/src/main/java/com/datagami/edudron/student/service/BulkStudentImportService.java`
  - Fixed URL encoding in `findUserByEmail()`
  - Added email normalization
  - Enhanced logging for auth header forwarding
  - Improved error messages with diagnostic hints

## Deployment

### Rebuild Services
```bash
./gradlew :identity:build
./gradlew :student:build
```

### Restart Services
Restart both identity and student services

### Verify
```bash
# Test the endpoint
./scripts/test-user-by-email-endpoint.sh test@example.com <token> <tenant-id>

# Try bulk import with existing users
# Check logs for case-insensitive search messages
```

## Expected Log Messages

### Before Fix
```
Finding user by email en22cs301959%40medicaps.ac.in for tenant: xxx
User already exists with this email in this tenant
User already exists but could not be found for update
```

### After Fix
```
GET /by-email - Received email parameter: 'en22cs301959@medicaps.ac.in'
Finding user by email en22cs301959@medicaps.ac.in for tenant: xxx (case-insensitive)
Found existing user by email: en22cs301959@medicaps.ac.in with id: xxx
Successfully updated existing user: en22cs301959@medicaps.ac.in (id: xxx)
```

## Testing

### Test Case 1: Mixed Case Existing User
```csv
email,name,phone,instituteId,classId,sectionId
EN22CS301959@medicaps.ac.in,Updated Name,1234567890,inst-123,class-456,section-789
```

**Expected**: User found and updated successfully (case-insensitive)

### Test Case 2: Different Case in CSV
Database has: `student@example.com`  
CSV has: `Student@Example.COM`

**Expected**: User found and updated (not created as duplicate)

### Test Case 3: JWT Token Case Mismatch
Database has: `admin@example.com`  
JWT contains: `Admin@Example.com`

**Expected**: Authentication works correctly

## Migration Notes

### No Migration Required

Spring Data JPA's `IgnoreCase` methods work with existing data:
- Uses `LOWER()` function in SQL queries
- No schema changes needed
- No data migration required
- Works with existing indexes

### Optional: Email Normalization

For consistency, you can normalize all existing emails to lowercase:

```sql
-- Optional: Normalize existing emails
UPDATE users SET email = LOWER(email);
```

**Benefits**:
- Consistent storage format
- Slightly faster queries (no LOWER() function needed)
- Cleaner database

**Not Required**: Case-insensitive queries work either way

## Related Issues Fixed

1. ✅ Bulk import upsert not working
2. ✅ "User already exists but could not be found" error
3. ✅ Double URL encoding in REST calls
4. ✅ Case-sensitive authentication lookups
5. ✅ Inconsistent email comparison across services

## Backward Compatibility

✅ **Fully backward compatible**
- Old case-sensitive methods still exist
- New case-insensitive methods added alongside
- No breaking changes to API
- Existing code continues to work

## Performance Impact

**Minimal**: Case-insensitive queries use `LOWER()` function:
```sql
-- Generated SQL
SELECT * FROM users 
WHERE LOWER(email) = LOWER(?) 
AND client_id = ?
```

**Optimization**: Consider adding functional index:
```sql
CREATE INDEX idx_users_email_lower ON users (LOWER(email), client_id);
```

## Summary

This fix resolves the fundamental issue of email case sensitivity throughout the application. All email-based lookups are now case-insensitive, matching user expectations and standard email behavior while maintaining data integrity.
