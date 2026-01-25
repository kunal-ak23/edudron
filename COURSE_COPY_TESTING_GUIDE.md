# Cross-Tenant Course Copy - Testing Guide

## Overview

This guide provides comprehensive testing instructions for the cross-tenant course copy feature. The feature allows SYSTEM_ADMIN users to copy complete courses (with all content and media) from one tenant to another asynchronously.

## Prerequisites

1. **User Authentication**: You must be logged in as a SYSTEM_ADMIN user
2. **Multiple Tenants**: At least 2 active tenants in the system
3. **Test Courses**: Courses with various content types in source tenant
4. **Redis Running**: Redis must be running for job queue
5. **Azure Storage**: Optional but recommended for testing media copying

## Testing Environments

### Backend API Testing (REST)

Base URL: `http://localhost:8080` (adjust port as needed)

Headers required:
```
Authorization: Bearer {jwt-token}
X-Client-Id: SYSTEM
Content-Type: application/json
```

### Frontend UI Testing

Navigate to: `http://localhost:3000/super-admin/course-copy`

Must be logged in as SYSTEM_ADMIN user.

## Test Scenarios

### 1. Simple Course Copy (Basic Validation)

**Objective**: Verify basic copy functionality with minimal content

**Test Data**:
- Source course: 1 section, 3 lectures, no media
- Target: Different tenant

**Steps**:

1. **Via API**:
```bash
POST /content/courses/{courseId}/copy-to-tenant
{
  "targetClientId": "target-tenant-uuid",
  "newCourseTitle": "Test Copy - Simple Course",
  "copyPublishedState": false
}
```

2. **Via UI**:
   - Select source tenant
   - Select target tenant  
   - Find simple course in list
   - Click "Copy to Target"
   - Leave default title
   - Click "Start Copy"

**Expected Results**:
- ✅ Returns job ID immediately (< 1 second)
- ✅ Job status changes: PENDING → QUEUED → PROCESSING → COMPLETED
- ✅ Progress bar shows 0% to 100%
- ✅ New course created in target tenant
- ✅ All sections and lectures copied
- ✅ New course has different ID
- ✅ New course belongs to target tenant
- ✅ Duration: ~5-10 seconds

**Verification**:
```bash
# Get job status
GET /content/courses/copy-jobs/{jobId}

# Get new course
GET /content/courses/{newCourseId}

# Verify it's in target tenant
# Check clientId matches targetClientId
```

### 2. Medium Course Copy (Content Variety)

**Objective**: Test copy with diverse content types

**Test Data**:
- Source course: 10 sections, 50 lectures
- Content types: Videos, PDFs, text, images
- 5 assessments with 50 quiz questions

**Steps**:
1. Submit copy job via API or UI
2. Monitor progress updates
3. Wait for completion

**Expected Results**:
- ✅ All content types copied
- ✅ All assessments and questions copied
- ✅ Quiz options correctly linked
- ✅ Section/lecture sequences preserved
- ✅ Duration: ~1-2 minutes

**Verification Queries**:
```sql
-- Check copied course
SELECT * FROM content.courses WHERE id = '{newCourseId}';

-- Verify sections count
SELECT COUNT(*) FROM content.sections WHERE course_id = '{newCourseId}';

-- Verify lectures count
SELECT COUNT(*) FROM content.lectures WHERE course_id = '{newCourseId}';

-- Verify assessments
SELECT COUNT(*) FROM content.assessments WHERE course_id = '{newCourseId}';

-- Verify quiz questions
SELECT COUNT(*) FROM content.quiz_questions 
WHERE assessment_id IN (
  SELECT id FROM content.assessments WHERE course_id = '{newCourseId}'
);
```

### 3. Large Course Copy (Performance Test)

**Objective**: Test async processing with large course

**Test Data**:
- Source course: 20+ sections, 100+ lectures
- 50+ video files (various sizes)
- 20+ PDF resources
- 15+ assessments

**Steps**:
1. Submit copy job
2. Monitor Redis queue
3. Check progress updates every 2 seconds
4. Wait for completion (may take 5-10 minutes)

**Expected Results**:
- ✅ No HTTP timeout errors
- ✅ Progress updates received regularly
- ✅ All 100+ lectures copied
- ✅ All media files copied (check Azure Blob Storage)
- ✅ Duration: ~5-10 minutes
- ✅ Backend remains responsive during copy

**Performance Metrics to Track**:
- Time per lecture: ~1-2 seconds
- Time per video file: ~5-10 seconds (depends on size)
- Memory usage: Should remain stable
- Database connections: No leaks

### 4. Media-Heavy Course Copy (Azure Storage Test)

**Objective**: Verify media duplication in Azure Blob Storage

**Test Data**:
- Course with 30+ video files
- 10+ PDF documents
- 20+ images (thumbnails, content images)

**Steps**:
1. Submit copy job
2. Monitor "Duplicating media files" step (85-95% progress)
3. Wait for completion
4. Verify new files in Azure Storage

**Expected Results**:
- ✅ All media URLs updated to new tenant path
- ✅ New blob paths: `/{targetClientId}/courses/{newCourseId}/...`
- ✅ Original files remain intact
- ✅ New files accessible via new URLs
- ✅ Thumbnails, videos, PDFs all copied

**Azure Storage Verification**:
```bash
# List blobs in target tenant path (using Azure CLI)
az storage blob list \
  --container-name edudron-media \
  --prefix "{targetClientId}/courses/{newCourseId}/" \
  --account-name {storage-account}
```

### 5. Categories and Tags (Auto-Creation Test)

**Objective**: Verify automatic category/tag creation in target tenant

**Test Data**:
- Source course with unique category "Advanced Programming"
- Tags: ["python", "backend", "api-design"]
- Target tenant: No matching categories/tags

**Steps**:
1. Copy course to target tenant
2. Check if categories/tags created

**Expected Results**:
- ✅ New category created in target tenant with same name
- ✅ All tags created in target tenant
- ✅ Course properly linked to new category/tags
- ✅ No duplicate categories/tags if they already exist

**Verification Queries**:
```sql
-- Check category created
SELECT * FROM content.course_categories 
WHERE client_id = '{targetClientId}' 
AND name = 'Advanced Programming';

-- Check tags created
SELECT * FROM content.course_tags 
WHERE client_id = '{targetClientId}' 
AND name IN ('python', 'backend', 'api-design');

-- Verify course link
SELECT category_id, tags FROM content.courses WHERE id = '{newCourseId}';
```

### 6. Published State Copy Test

**Objective**: Verify published state handling

**Test Cases**:

**6a. Copy as unpublished (default)**:
```json
{
  "targetClientId": "...",
  "copyPublishedState": false
}
```
Expected: New course is unpublished (isPublished = false, publishedAt = null)

**6b. Copy as published**:
```json
{
  "targetClientId": "...",
  "copyPublishedState": true
}
```
Expected: New course is published (isPublished = true, publishedAt = current timestamp)

### 7. Authorization Test (Security)

**Objective**: Verify only SYSTEM_ADMIN can access

**Test Cases**:

**7a. Non-admin user**:
- Login as TENANT_ADMIN or INSTRUCTOR
- Try to access `/super-admin/course-copy`
- Expected: Access denied message

**7b. Non-admin API call**:
```bash
POST /content/courses/{courseId}/copy-to-tenant
Authorization: Bearer {non-admin-jwt-token}
```
Expected: 403 Forbidden or 401 Unauthorized

**7c. SYSTEM_ADMIN success**:
- Login as SYSTEM_ADMIN
- Access UI and API endpoints
- Expected: Full access granted

### 8. Error Handling Tests

**8a. Source Course Not Found**:
```bash
POST /content/courses/invalid-course-id/copy-to-tenant
{
  "targetClientId": "valid-tenant-id"
}
```
Expected: Job fails with "Source course not found" error

**8b. Invalid Target Tenant**:
```bash
POST /content/courses/{validCourseId}/copy-to-tenant
{
  "targetClientId": "invalid-tenant-uuid"
}
```
Expected: Job fails with tenant validation error

**8c. Media Copy Failure**:
- Use course with invalid media URLs
- Expected: Job completes but logs media copy errors, continues with other files

**8d. Network Issues**:
- Disconnect from Azure Storage during copy
- Expected: Job fails gracefully with error message

### 9. Concurrent Copy Test

**Objective**: Verify multiple copy jobs can run

**Steps**:
1. Submit 3 copy jobs for different courses simultaneously
2. Monitor all job IDs
3. Verify all complete successfully

**Expected Results**:
- ✅ All jobs processed (may be sequential due to queue)
- ✅ No race conditions
- ✅ Each job independent
- ✅ No data corruption

### 10. UI Functionality Tests

**10a. Tenant Selection**:
- ✅ Source tenant dropdown loads all tenants
- ✅ Target tenant dropdown excludes source tenant
- ✅ Courses load when source tenant selected

**10b. Course Search**:
- ✅ Search filters courses by title/description
- ✅ Real-time filtering as user types
- ✅ Shows "No courses found" when appropriate

**10c. Copy Dialog**:
- ✅ Shows source course details
- ✅ Shows target tenant name
- ✅ Optional title input works
- ✅ Publish state checkbox works
- ✅ "Copy to Target" button disabled until target tenant selected

**10d. Progress Display**:
- ✅ Progress bar updates smoothly (0-100%)
- ✅ Status message shows current step
- ✅ Success state shows summary
- ✅ Failure state shows error message
- ✅ Can close dialog after completion

## Manual Testing Checklist

### Backend Tests
- [ ] Unit tests pass (`./gradlew test`)
- [ ] Course copy endpoint returns 202 ACCEPTED
- [ ] Job status endpoint returns job details
- [ ] SYSTEM_ADMIN validation works
- [ ] Redis queue receives jobs
- [ ] Background processor picks up jobs
- [ ] Progress updates in Redis
- [ ] Job completion stores result

### Frontend Tests
- [ ] Page loads for SYSTEM_ADMIN
- [ ] Access denied for non-admin
- [ ] Tenant dropdowns populate
- [ ] Course list loads
- [ ] Search functionality works
- [ ] Copy dialog opens
- [ ] Job submission works
- [ ] Progress polling works
- [ ] Success/failure states display correctly
- [ ] Can perform multiple copies

### Integration Tests
- [ ] End-to-end: Submit → Process → Complete
- [ ] Small course (< 1 minute)
- [ ] Medium course (1-2 minutes)
- [ ] Large course (5-10 minutes)
- [ ] Course with all content types
- [ ] Course with media files
- [ ] Course with assessments
- [ ] Categories/tags auto-created
- [ ] Media files copied to Azure

### Database Verification
- [ ] New course has correct client_id
- [ ] All sections copied with correct sequences
- [ ] All lectures copied with correct relationships
- [ ] All assessments and questions copied
- [ ] Learning objectives copied
- [ ] Course resources copied
- [ ] No instructor associations copied
- [ ] Timestamps set correctly

### Azure Storage Verification
- [ ] Media files exist in new tenant path
- [ ] File sizes match originals
- [ ] URLs work (files accessible)
- [ ] Original files still exist
- [ ] No broken media links in new course

## Performance Benchmarks

| Course Size | Expected Duration | Test Result | Status |
|-------------|------------------|-------------|---------|
| 5 lectures, 0 media | ~10 seconds | | ⬜ |
| 10 lectures, 5 videos | ~30 seconds | | ⬜ |
| 50 lectures, 20 videos | ~2 minutes | | ⬜ |
| 100 lectures, 50 videos | ~10 minutes | | ⬜ |

## Common Issues & Troubleshooting

### Issue: Job stays in PENDING status

**Possible Causes**:
- Redis not running
- AIQueueProcessor not started
- Queue processor error

**Debug**:
```bash
# Check Redis
redis-cli ping

# Check Redis queue
redis-cli LLEN "ai:queue:course-copy"

# Check job in Redis
redis-cli GET "ai:job:{jobId}"
```

### Issue: Media files not copying

**Possible Causes**:
- BlobServiceClient not configured
- Invalid Azure credentials
- Network issues

**Debug**:
- Check application.yml for azure.storage.* properties
- Verify Azure credentials
- Check backend logs for media copy errors

### Issue: Progress stuck at certain percentage

**Possible Causes**:
- Large media file taking time
- Network slow
- Transaction timeout

**Solution**:
- Wait longer (large files can take minutes)
- Check backend logs
- Monitor Azure Storage copy operations

## Testing Tools

### Postman Collection

```json
{
  "info": {
    "name": "Course Copy API Tests"
  },
  "item": [
    {
      "name": "Submit Course Copy",
      "request": {
        "method": "POST",
        "url": "{{baseUrl}}/content/courses/{{courseId}}/copy-to-tenant",
        "header": [
          {"key": "Authorization", "value": "Bearer {{token}}"},
          {"key": "X-Client-Id", "value": "SYSTEM"}
        ],
        "body": {
          "mode": "raw",
          "raw": "{\"targetClientId\": \"{{targetTenantId}}\"}"
        }
      }
    },
    {
      "name": "Get Job Status",
      "request": {
        "method": "GET",
        "url": "{{baseUrl}}/content/courses/copy-jobs/{{jobId}}",
        "header": [
          {"key": "Authorization", "value": "Bearer {{token}}"}
        ]
      }
    }
  ]
}
```

### cURL Commands

**Submit Copy Job**:
```bash
curl -X POST "http://localhost:8080/content/courses/{courseId}/copy-to-tenant" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "X-Client-Id: SYSTEM" \
  -H "Content-Type: application/json" \
  -d '{
    "targetClientId": "target-tenant-uuid",
    "newCourseTitle": "Copy of Original",
    "copyPublishedState": false
  }'
```

**Poll Job Status**:
```bash
curl "http://localhost:8080/content/courses/copy-jobs/{jobId}" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Database Queries

**Count copied entities**:
```sql
-- After copy, run these queries
SELECT 
  (SELECT COUNT(*) FROM content.courses WHERE id = '{newCourseId}') as courses,
  (SELECT COUNT(*) FROM content.sections WHERE course_id = '{newCourseId}') as sections,
  (SELECT COUNT(*) FROM content.lectures WHERE course_id = '{newCourseId}') as lectures,
  (SELECT COUNT(*) FROM content.assessments WHERE course_id = '{newCourseId}') as assessments,
  (SELECT COUNT(*) FROM content.course_resources WHERE course_id = '{newCourseId}') as resources;
```

**Compare source vs target**:
```sql
SELECT 
  'Source' as source,
  COUNT(*) as lecture_count,
  SUM(duration_seconds) as total_duration
FROM content.lectures 
WHERE course_id = '{sourceCourseId}'

UNION ALL

SELECT 
  'Target' as source,
  COUNT(*) as lecture_count,
  SUM(duration_seconds) as total_duration
FROM content.lectures 
WHERE course_id = '{newCourseId}';
```

## Automated Test Execution

### Run Backend Tests

```bash
cd /Users/kunalsharma/datagami/edudron/content

# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests CourseCopyServiceTest

# Run with logging
./gradlew test --info
```

### Expected Test Results

All tests should pass:
- `CourseCopyServiceTest`: 6 tests
  - testCopyCourseToTenant_Success
  - testCopyCourseToTenant_SourceCourseNotFound
  - testCopyCourseEntity_WithCustomTitle
  - testCopySections_PreservesSequence
  - testCopyAssessments_WithQuestions
  - testProgressCallbacks

- `MediaCopyServiceTest`: 9 tests
  - testDuplicateAllMedia_NoBlobServiceClient
  - testDuplicateAllMedia_WithCourseMedia
  - testDuplicateAllMedia_WithProgressCallback
  - testDuplicateAllMedia_WithLectureContent
  - testCopyMediaFile_NullUrl
  - testCopyMediaFile_EmptyUrl
  - testCopyMediaFile_NonBlobUrl
  - testDuplicateAllMedia_HandlesErrors
  - testDuplicateAllMedia_SkipsNonAzureUrls
  - testDuplicateAllMedia_DeduplicatesUrls

## Load Testing

### Test Concurrent Operations

```bash
# Submit 5 copy jobs simultaneously
for i in {1..5}; do
  curl -X POST "http://localhost:8080/content/courses/{courseId}/copy-to-tenant" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Client-Id: SYSTEM" \
    -H "Content-Type: application/json" \
    -d "{\"targetClientId\": \"$TARGET_TENANT\"}" &
done
wait
```

Expected: All jobs complete successfully (may be processed sequentially)

### Monitor System Resources

```bash
# Monitor Redis memory
redis-cli INFO memory

# Monitor backend memory (if using Docker)
docker stats edudron-content

# Monitor database connections
# Check pg_stat_activity in PostgreSQL
```

## Test Data Setup

### Create Test Tenants

```sql
-- Source tenant
INSERT INTO idp.clients (id, name, slug, is_active) 
VALUES (gen_random_uuid(), 'Test Source Tenant', 'test-source', true);

-- Target tenant
INSERT INTO idp.clients (id, name, slug, is_active) 
VALUES (gen_random_uuid(), 'Test Target Tenant', 'test-target', true);
```

### Create Test Course

```bash
POST /content/courses
{
  "title": "Test Course for Copying",
  "description": "A comprehensive test course",
  "isFree": true,
  "isPublished": true,
  "difficultyLevel": "INTERMEDIATE",
  "language": "en"
}
```

## Success Criteria

✅ All unit tests pass  
✅ API endpoints respond correctly  
✅ Async processing works without timeouts  
✅ Progress tracking updates regularly  
✅ All course entities copied correctly  
✅ Media files duplicated successfully  
✅ Categories/tags auto-created  
✅ Security validation works  
✅ UI displays correctly and polls status  
✅ Error handling graceful  
✅ No memory leaks or database connection issues  
✅ Multiple copy operations work  

## Bug Reporting

If you find issues, report with:

1. **Environment**: Development/Staging/Production
2. **Source Course ID**: `{courseId}`
3. **Target Tenant ID**: `{tenantId}`
4. **Job ID**: `{jobId}`
5. **Error Message**: From job.error or logs
6. **Steps to Reproduce**: Detailed steps
7. **Expected vs Actual**: What should happen vs what happened
8. **Backend Logs**: From content service
9. **Redis State**: Job data from Redis
10. **Screenshots**: If UI issue

## Next Steps After Testing

1. ✅ Fix any bugs found
2. ✅ Performance optimization if needed
3. ✅ Add monitoring/alerting for production
4. ✅ Document API in Swagger
5. ✅ Create user guide for super admins
6. ✅ Consider adding:
   - Dry-run mode (preview what will be copied)
   - Selective copying (specific sections only)
   - Batch copy (multiple courses at once)
   - WebSocket for real-time updates

---

**Testing Date**: January 25, 2026  
**Feature Status**: Ready for Testing  
**Priority**: High (Core super admin functionality)
