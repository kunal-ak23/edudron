# Bulk Import Upsert Fix - Deployment Checklist

## What Was Fixed

✅ **CRITICAL**: Added case-insensitive email lookups throughout the system  
✅ Fixed double URL encoding in REST template calls  
✅ Added email normalization (lowercase) throughout the bulk import flow  
✅ Enhanced logging to show exactly what's happening during upsert  
✅ Better error messages to diagnose issues  
✅ Added test script to verify the new endpoint
✅ Fixed authentication context lookups to be case-insensitive

## Deployment Steps

### 1. Rebuild Services

```bash
# From edudron root directory
./gradlew :identity:build
./gradlew :student:build
```

### 2. Restart Services

Restart both the **identity** and **student** services using your deployment method:

- **Docker Compose**: `docker-compose restart identity student`
- **Kubernetes**: `kubectl rollout restart deployment/identity deployment/student`
- **Local**: Stop and restart both services

### 3. Verify Deployment

Run the test script to verify the new endpoint is working:

```bash
cd scripts
./test-user-by-email-endpoint.sh student@example.com <your-jwt-token> <tenant-id>
```

Expected results:
- ✅ **200 OK** if user exists
- ✅ **404 Not Found** if user doesn't exist (this is normal)
- ❌ **Endpoint not found** means the service needs to be redeployed

### 4. Test Bulk Import

1. Go to Admin Dashboard → Students → Import
2. Create a test CSV with an existing student's email
3. Check the "Update existing students (by email)" checkbox
4. Upload the file
5. Check the results - should show successful update

## Quick Diagnosis

If you're still seeing "User already exists but could not be found for update":

### Check 1: Is the new endpoint deployed?

```bash
./scripts/test-user-by-email-endpoint.sh test@example.com <token> <tenant-id>
```

If you get "endpoint not found" → **Redeploy identity service**

### Check 2: Check the logs

Look for these log messages in the **student service**:

```
Processing row 1: email=test@example.com (original: Test@Example.com), upsertExisting=true
Attempting to find user by email using endpoint: http://localhost:8080/idp/users/by-email?email=test%40example.com
```

If you see:
- `404 from identity service` → Endpoint not deployed
- `403 Forbidden` → Permission issue
- `clientId mismatch` → Tenant context problem
- `Found existing user` → It's working! ✅

### Check 3: Email case sensitivity

If users were created with mixed case emails (User@Example.com), normalize them:

```sql
UPDATE users SET email = LOWER(email);
```

## Files Changed

### New Files
- `/scripts/test-user-by-email-endpoint.sh` - Test script for new endpoint
- `/BULK_IMPORT_UPSERT_FIX.md` - Detailed fix documentation
- `/BULK_IMPORT_DEPLOYMENT_CHECKLIST.md` - This file

### Modified Files
- `identity/src/main/java/com/datagami/edudron/identity/web/UserController.java`
  - Added `GET /idp/users/by-email` endpoint
  
- `identity/src/main/java/com/datagami/edudron/identity/service/UserService.java`
  - Added `getUserByEmail()` method
  - Added missing `Optional` import
  
- `student/src/main/java/com/datagami/edudron/student/service/BulkStudentImportService.java`
  - Email normalization to lowercase
  - Enhanced logging throughout
  - Improved error messages with diagnostic hints
  - Better error handling in `findUserByEmail()`

## Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| "User exists but not found" | **Case sensitivity** | ✅ FIXED - Now using case-insensitive queries |
| "endpoint not found" | Identity service not rebuilt | Rebuild and restart identity service |
| "403 Forbidden" | Permission issue | Check service-to-service auth |
| "clientId mismatch" | Wrong tenant context | Verify X-Client-Id header |
| URL-encoded email in logs | Double encoding | ✅ FIXED - Using URI templates now |

## Support

If you're still having issues after following this checklist:

1. Check both identity and student service logs
2. Run the test script and share the output
3. Share the relevant log lines from bulk import attempt
4. Verify the services were actually restarted (check process start time or deployment timestamp)
