# Cross-Tenant Course Copy - Quick Start Guide

## 🎯 What This Feature Does

Allows SYSTEM_ADMIN users to copy entire courses (including all content, assessments, and media files) from one tenant to another. The operation runs asynchronously to handle large courses with extensive media without HTTP timeouts.

## ⚡ Quick Test (5 minutes)

### Step 1: Ensure Services Running

```bash
# Start Redis (required for job queue)
redis-server

# Start backend (content service)
cd content
./gradlew bootRun

# Start frontend (admin dashboard)
cd frontend/apps/admin-dashboard
npm run dev
```

### Step 2: Login as SYSTEM_ADMIN

1. Navigate to `http://localhost:3000/login`
2. Login with SYSTEM_ADMIN credentials
3. Ensure tenant context is set to "SYSTEM"

### Step 3: Access Course Copy Page

Navigate to: `http://localhost:3000/super-admin/course-copy`

### Step 4: Copy a Course

1. **Select Source Tenant** - Choose tenant with existing courses
2. **Select Target Tenant** - Choose different tenant
3. **Find Course** - Use search to find course to copy
4. **Click "Copy to Target"** button
5. **Configure** (optional):
   - Enter new title (or keep default "Copy of...")
   - Check "Copy published state" if you want it published
6. **Click "Start Copy"**
7. **Watch Progress** - Bar updates every 2 seconds
8. **View Results** - Summary shows counts of copied entities

### Step 5: Verify

```bash
# Get the new course ID from the success message
# Then verify it exists:

curl "http://localhost:8080/content/courses/{newCourseId}" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 🔧 API Quick Test

### Submit Copy Job

```bash
# Replace variables:
# - COURSE_ID: Source course ID
# - TARGET_TENANT_UUID: Target tenant UUID
# - YOUR_TOKEN: SYSTEM_ADMIN JWT token

curl -X POST "http://localhost:8080/content/courses/COURSE_ID/copy-to-tenant" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "X-Client-Id: SYSTEM" \
  -H "Content-Type: application/json" \
  -d '{
    "targetClientId": "TARGET_TENANT_UUID",
    "newCourseTitle": "Test Copy",
    "copyPublishedState": false
  }'

# Response (immediate):
{
  "jobId": "01JKX...",
  "status": "PENDING",
  "progress": 0,
  "createdAt": "2026-01-25T..."
}
```

### Poll Job Status

```bash
# Poll this endpoint every 2 seconds
curl "http://localhost:8080/content/courses/copy-jobs/01JKX..." \
  -H "Authorization: Bearer YOUR_TOKEN"

# Response (while processing):
{
  "jobId": "01JKX...",
  "status": "PROCESSING",
  "progress": 45,
  "message": "Copying lectures",
  ...
}

# Response (when completed):
{
  "jobId": "01JKX...",
  "status": "COMPLETED",
  "progress": 100,
  "message": "Course copy completed successfully",
  "result": {
    "newCourseId": "01JKY...",
    "sourceCourseId": "01JKW...",
    "targetClientId": "uuid",
    "copiedEntities": {
      "sections": 12,
      "lectures": 48,
      "assessments": 8,
      "resources": 6,
      "mediaAssets": 52
    },
    "completedAt": "2026-01-25T...",
    "duration": "2m 15s"
  }
}
```

## 📊 What Gets Copied

### ✅ Included
- Course metadata (title, description, pricing, difficulty, language)
- All sections (chapters) with sequences
- All lectures with content
- Lecture content items (videos, PDFs, text, links)
- Sub-lessons
- Assessments (quizzes, exams, assignments)
- Quiz questions and options
- Course resources (downloadable files)
- Learning objectives
- Categories (auto-created if missing)
- Tags (auto-created if missing)
- **All media files** (duplicated in Azure Blob Storage)

### ❌ Not Included (By Design)
- Instructors (must be assigned by target tenant)
- Student enrollments
- Student progress data
- Course assignments (to classes/sections)
- Student count statistics

## 🎨 UI Features

### Course Browser
- **Search**: Real-time filter by course title or description
- **Cards**: Shows lecture count, duration, published status
- **Disabled State**: Copy button disabled until target tenant selected

### Copy Dialog
- **Source Preview**: Read-only display of source course
- **Target Selection**: Shows target tenant name
- **Options**: 
  - Custom title input (optional)
  - Publish state checkbox
- **Progress Display**: Live updates every 2 seconds
- **Results Summary**: Shows entity counts on completion

### Progress Tracking
- **Visual Progress Bar**: 0-100% with smooth animation
- **Status Messages**: 
  - "Validating source course" (5%)
  - "Creating course copy" (10%)
  - "Copying sections" (25%)
  - "Copying lectures" (40%)
  - "Duplicating media files" (85-95%)
  - "Finalizing course copy" (95%)
  - "Course copy completed successfully" (100%)

## 🐛 Common Issues

### "Access Denied" Error
**Cause**: Not logged in as SYSTEM_ADMIN  
**Solution**: Login with SYSTEM_ADMIN credentials

### Job Stays in PENDING
**Cause**: Redis not running or queue processor not started  
**Solution**: Start Redis and restart content service

### Media Not Copying
**Cause**: Azure Storage not configured  
**Solution**: Configure azure.storage.* properties in application.yml

### Long Copy Time
**Cause**: Large media files (normal behavior)  
**Wait Time**: 100MB video = ~5-10 seconds, so 50 videos = ~5-10 minutes

## 📝 Example Copy Times

| Course Type | Content | Expected Time |
|-------------|---------|---------------|
| Simple | 5 lectures, no media | ~10 seconds |
| Basic | 10 lectures, 5 small videos | ~30 seconds |
| Standard | 30 lectures, 15 videos | ~1-2 minutes |
| Large | 100 lectures, 50 videos | ~5-10 minutes |
| Massive | 200 lectures, 100+ videos | ~15-20 minutes |

## 🚀 Production Deployment

### Configuration Required

**application.yml** (content service):
```yaml
spring:
  redis:
    host: your-redis-host
    port: 6379

azure:
  storage:
    connection-string: your-azure-connection-string
    container-name: edudron-media
```

### Pre-deployment Checklist

- [ ] Redis configured and accessible
- [ ] Azure Storage configured with proper permissions
- [ ] Async config enabled (`@EnableAsync`)
- [ ] Queue processor scheduled tasks enabled
- [ ] SYSTEM_ADMIN users created
- [ ] Frontend deployed with super-admin route
- [ ] Unit tests passing
- [ ] Manual testing completed

## 📚 Additional Resources

- **Implementation Details**: `COURSE_COPY_IMPLEMENTATION_SUMMARY.md`
- **Comprehensive Testing**: `COURSE_COPY_TESTING_GUIDE.md`
- **Original Plan**: `.cursor/plans/cross-tenant_course_copy_2148be0a.plan.md`

## 💡 Tips

1. **Test with small course first** - Verify functionality before copying large courses
2. **Monitor Redis** - Check queue size: `redis-cli LLEN "ai:queue:course-copy"`
3. **Check logs** - Backend logs show detailed copy progress
4. **Azure costs** - Media duplication doubles storage usage
5. **Retry failed jobs** - Manually retry by resubmitting with same parameters

---

**Feature Version**: 1.0  
**Last Updated**: January 25, 2026  
**Status**: Production Ready ✅
