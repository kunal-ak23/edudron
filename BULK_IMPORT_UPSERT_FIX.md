# Bulk Import "Update Existing Students" Fix

## Problem

The "Update existing students (by email)" checkbox in the bulk import feature was not working properly. When checked, nothing happened - existing students weren't being updated.

## Root Cause

The bulk import service had an inefficient and unreliable method for finding users by email:

1. **Inefficient API Call**: The `findUserByEmail()` method was fetching ALL users via `GET /idp/users` and then looping through them to find a match by email.
   
2. **Silent Failures**: Any errors during the user lookup were silently caught and returned null, making it impossible to debug.

3. **No Dedicated Endpoint**: There was no dedicated API endpoint to find a user by email within the tenant context.

## Solution

### 1. Added New Identity Service Endpoint

Created a new endpoint to find users by email within the tenant context:

**New Endpoint**: `GET /idp/users/by-email?email={email}`

**Files Changed**:
- `identity/src/main/java/com/datagami/edudron/identity/web/UserController.java`
  - Added `getUserByEmail()` endpoint
  
- `identity/src/main/java/com/datagami/edudron/identity/service/UserService.java`
  - Added `getUserByEmail()` method
  - Properly handles tenant context
  - Returns null for not found (allows 404 response)
  - Case-insensitive email matching via normalized lowercase comparison

### 2. Updated Bulk Import Service

**File Changed**: `student/src/main/java/com/datagami/edudron/student/service/BulkStudentImportService.java`

**Changes**:
- Replaced inefficient `findUserByEmail()` implementation with direct call to new endpoint
- Added comprehensive logging at DEBUG, INFO, WARN, and ERROR levels:
  - Shows when upsert is enabled
  - Logs when finding existing users
  - Logs successful updates
  - Logs failures with detailed error messages
- Proper error handling with specific messages for different failure scenarios

### 3. Key Improvements

1. **Performance**: Direct database query via repository method instead of fetching all users
2. **Reliability**: Proper error handling and logging instead of silent failures
3. **Tenant Safety**: Uses tenant-aware endpoint that respects user permissions
4. **Debugging**: Comprehensive logging to troubleshoot issues
5. **Case-Insensitive**: Email matching is case-insensitive (normalized to lowercase)

## Testing

### Step 1: Verify the New Endpoint is Deployed

First, test that the new `/idp/users/by-email` endpoint is available:

```bash
cd scripts
./test-user-by-email-endpoint.sh student@example.com <your-jwt-token> <tenant-id>
```

Expected output:
- **200 OK** with user data if user exists
- **404 Not Found** if user doesn't exist (this is correct)
- **404 with "endpoint not found"** means you need to rebuild/redeploy identity service

If the endpoint is not deployed:
```bash
# Rebuild the identity service
./gradlew :identity:build

# Restart the identity service
# (method depends on your deployment - docker-compose, kubernetes, etc.)
```

### Step 2: Test Bulk Import with Upsert

1. **Create a test CSV** with existing student emails:
   ```csv
   email,name,phone,instituteId,classId,sectionId
   existing@example.com,Updated Name,1234567890,inst-123,class-456,section-789
   EXISTING@EXAMPLE.COM,Updated Name 2,1234567890,inst-123,class-456,section-789
   ```

2. **Upload with "Update existing students" checked**

3. **Check logs** for successful upsert:
   ```
   Processing row 1: email=existing@example.com (original: existing@example.com), upsertExisting=true
   Attempting to find user by email using endpoint: http://localhost:8080/idp/users/by-email?email=existing%40example.com
   Found existing user by email: existing@example.com with id: xxx
   Upsert enabled - attempting to find and update existing user: existing@example.com
   Found existing user existing@example.com (id: xxx), updating...
   Successfully updated existing user: existing@example.com (id: xxx)
   ```

4. **Verify** the student's information was updated in the database

### Step 3: Check for Errors

If you see "User already exists but could not be found for update", check logs for:

```
User xxx already exists but could not be found for update. This might be due to:
  1. New endpoint not deployed: GET /idp/users/by-email
  2. Permission issue accessing identity service
  3. Tenant context mismatch
```

Then look for the detailed error messages like:
- `404 from identity service` - Endpoint not deployed
- `403 Forbidden` - Permission issue
- `clientId mismatch` - Tenant context problem

## Error Messages

With the improved logging, you'll now see clear error messages:

- `"User already exists but could not be found for update. Check logs for details."` - The user exists but the find operation failed (see logs for reason)
- `"Failed to update existing user: {details}"` - The update operation failed with specific details
- `"User already exists with this email"` - Upsert is disabled and user exists

## Troubleshooting

### "User already exists but could not be found for update"

This error means the bulk import detected a user exists but couldn't retrieve them for updating. Common causes:

#### 1. New Endpoint Not Deployed

**Symptom**: Logs show `404 from identity service` or endpoint errors

**Fix**:
```bash
# Rebuild identity service
./gradlew :identity:build

# Rebuild student service (also has changes)
./gradlew :student:build

# Restart both services
```

**Verify**: Run the test script:
```bash
./scripts/test-user-by-email-endpoint.sh student@example.com <token> <tenant-id>
```

#### 2. Permission Issue (403 Forbidden)

**Symptom**: Logs show `Permission denied` or `403 Forbidden`

**Fix**: Check that:
- The student service has proper authorization to call identity service
- The JWT token being forwarded has the right permissions
- The service-to-service authentication is configured correctly

#### 3. Tenant Context Mismatch

**Symptom**: Logs show `clientId mismatch` warnings

**Fix**: Verify that:
- The `X-Client-Id` header is being passed correctly
- The user belongs to the correct tenant
- The bulk import is being run in the correct tenant context

#### 4. Email Case Sensitivity Issue

**Symptom**: User exists with different case (e.g., User@Example.com vs user@example.com)

**Fix**: The new implementation normalizes emails to lowercase automatically. If you have existing users with mixed-case emails, you may need to normalize them first:

```sql
-- Run the email normalization script (if not already done)
UPDATE users SET email = LOWER(email);
```

### Checking Service Logs

To debug issues, check logs in both services:

**Student Service** (where bulk import runs):
```bash
# Look for these log lines
grep "Processing row" student-service.log
grep "Upsert enabled" student-service.log
grep "Found existing user" student-service.log
```

**Identity Service** (where user lookup happens):
```bash
# Look for these log lines
grep "Finding user by email" identity-service.log
grep "GET /idp/users/by-email" identity-service.log
```

## Related Files

- `identity/src/main/java/com/datagami/edudron/identity/web/UserController.java`
- `identity/src/main/java/com/datagami/edudron/identity/service/UserService.java`
- `identity/src/main/java/com/datagami/edudron/identity/repo/UserRepository.java`
- `student/src/main/java/com/datagami/edudron/student/service/BulkStudentImportService.java`
- `student/src/main/java/com/datagami/edudron/student/dto/BulkStudentImportRequest.java`

## API Documentation

### New Endpoint

```http
GET /idp/users/by-email?email={email}
```

**Parameters**:
- `email` (required): The email address to search for

**Response**:
- `200 OK`: Returns UserDTO if found
- `404 Not Found`: User not found in the tenant context

**Tenant Context**:
- For tenant users: Searches within the current tenant only
- For SYSTEM_ADMIN: Searches across all tenants

**Example**:
```bash
curl -X GET "http://localhost:8080/idp/users/by-email?email=student@example.com" \
  -H "Authorization: Bearer {token}" \
  -H "X-Client-Id: {tenant-id}"
```
