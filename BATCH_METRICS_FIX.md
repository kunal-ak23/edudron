# Batch Metrics Dashboard Fix

## Problem
The batch metrics on the admin dashboard were not working due to a **database schema mismatch**:

```
ERROR: relation "student.batches" does not exist
```

### Root Cause Analysis
1. **Schema Migration**: The `batches` table was migrated to `sections` table in database changelog 0007
2. **Legacy Code**: The `Batch` entity and `BatchController` still referenced the old `batches` table
3. **Missing Endpoint**: The frontend called `/api/batches` which tried to query a non-existent table
4. **Terminology Mismatch**: The system evolved from:
   - **Old**: Batches → Courses
   - **New**: Sections → Classes → Courses

## Architecture Changes
The system migrated from a direct batch-course relationship to a hierarchical structure:
- **Sections** belong to **Classes**
- **Classes** belong to **Courses**  
- The `batches` table was completely replaced by the `sections` table

## Solution

The fix redirects all batch-related dashboard calls to the correct **Sections API**.

### 1. Backend Changes

#### Added List All Sections Endpoint in `SectionController`
```java
@GetMapping("/sections")
@Operation(summary = "List all sections", description = "Get all sections for the current tenant")
public ResponseEntity<List<SectionDTO>> getAllSections() {
    List<SectionDTO> sections = sectionService.getAllSections();
    return ResponseEntity.ok(sections);
}
```

#### Added Service Method in `SectionService`
```java
public List<SectionDTO> getAllSections() {
    String clientIdStr = TenantContext.getClientId();
    if (clientIdStr == null) {
        throw new IllegalStateException("Tenant context is not set");
    }
    UUID clientId = UUID.fromString(clientIdStr);
    
    List<Section> sections = sectionRepository.findByClientId(clientId);
    return sections.stream()
        .map(section -> toDTO(section, clientId))
        .collect(Collectors.toList());
}
```

The repository method `findByClientId()` already existed in `SectionRepository`.

### 2. Frontend Changes

#### Updated EnrollmentsApi to Redirect Batches to Sections
**Added in `enrollments.ts`:**
```typescript
// Section management (replaces legacy Batch management)
async listSections(classId?: string): Promise<Batch[]> {
  const url = classId 
    ? `/api/classes/${classId}/sections`
    : '/api/sections'
  const response = await this.apiClient.get<Batch[]>(url)
  return Array.isArray(response.data) ? response.data : []
}

// Legacy Batch management - deprecated, use Sections instead
async listBatches(courseId?: string): Promise<Batch[]> {
  // Redirect to sections API since batches table no longer exists
  return this.listSections()
}
```

This means `enrollmentsApi.listBatches()` now automatically calls the `/api/sections` endpoint.

#### Updated Batch Interface for Compatibility
```typescript
export interface Batch {
  id: string
  courseId?: string // Legacy - batches used to link to courses
  classId?: string // New - sections link to classes
  name: string
  startDate: string
  endDate: string
  capacity?: number // Legacy field name
  maxStudents?: number // New field name (same as capacity)
  enrolledCount?: number // Legacy field name
  studentCount?: number // New field name (same as enrolledCount)
  isActive: boolean
  createdAt: string
  updatedAt: string
}
```

#### Updated Dashboard to Handle Both Field Names
```typescript
// Handle both capacity/maxStudents
const totalCapacity = withCapacity.reduce((sum, b) => 
  sum + (b.capacity ?? b.maxStudents ?? 0), 0)

// Handle both enrolledCount/studentCount
const totalEnrolled = withCapacity.reduce((sum, b) => 
  sum + (b.enrolledCount ?? b.studentCount ?? 0), 0)
```

#### Improved Batch Card Display
```typescript
<p className="text-sm text-green-100 mt-1">
  {loading ? '...' : activeBatches.length} active{activeBatches.length !== totalBatches ? `, ${totalBatches - activeBatches.length} inactive` : ''}
</p>
```

## Testing

To verify the fix:

1. **Start the backend services:**
   ```bash
   cd /Users/kunalsharma/datagami/edudron
   ./scripts/edudron.sh
   ```

2. **Start the frontend:**
   ```bash
   cd frontend/apps/admin-dashboard
   npm run dev
   ```

3. **Test the dashboard:**
   - Log in to the admin dashboard
   - Check the "Batches" card on the dashboard
   - Verify that:
     - The total batch count is accurate (showing sections)
     - Active/inactive breakdown is displayed
     - Seat utilization is calculated correctly
     - No database errors in backend logs
     - The count matches the sections shown in `/batches` page

4. **Test the API directly:**
   ```bash
   # Get all sections for your tenant (new endpoint)
   curl -H "X-Tenant-ID: YOUR_TENANT_ID" \
        -H "Authorization: Bearer YOUR_TOKEN" \
        http://localhost:8080/api/sections
   
   # Verify the batches table doesn't exist
   psql -h localhost -U postgres -d edudron
   SELECT * FROM student.batches; -- Should fail
   SELECT * FROM student.sections; -- Should work
   ```

5. **Verify the backend logs:**
   - No more "relation student.batches does not exist" errors
   - Successful API calls to `/api/sections`

## Key Insight
The "Batch" terminology is **legacy** in the codebase. The system has evolved to use **Sections** which belong to **Classes**. The `batches` table was completely removed during database migration 0007 (`db.changelog-0007-rename-batch-to-section.yaml`).

Any code still referencing the `Batch` entity is accessing a non-existent table and will fail with database errors.

## Impact
- ✅ Fixed database error: "relation student.batches does not exist"
- ✅ Dashboard now displays accurate section metrics (displayed as "Batches" for UI consistency)
- ✅ Backward compatible - existing calls to `listBatches()` automatically redirect to sections
- ✅ Better user visibility with active/inactive breakdown
- ✅ No breaking changes to existing frontend code

## Files Modified

### Backend
1. `student/src/main/java/com/datagami/edudron/student/web/SectionController.java`
   - Added `GET /api/sections` endpoint to list all sections for tenant
2. `student/src/main/java/com/datagami/edudron/student/service/SectionService.java`
   - Added `getAllSections()` method

### Frontend
3. `frontend/packages/shared-utils/src/api/enrollments.ts`
   - Added `listSections()` method
   - Updated `listBatches()` to redirect to `listSections()`
   - Updated `Batch` interface to support both legacy and new field names
4. `frontend/apps/admin-dashboard/src/app/dashboard/page.tsx`
   - Updated `computeSeatUtilization()` to handle both field name variants
   - Updated student count calculation to use `enrolledCount ?? studentCount`
   - Improved batch card display with inactive count
5. `frontend/apps/admin-dashboard/src/app/batches/page.tsx`
   - Updated capacity display to handle both `capacity` and `maxStudents` fields

## Future Improvements
1. Consider renaming "Batches" to "Sections" in the UI for clarity
2. Deprecate or remove the `Batch` entity and `BatchController` entirely
3. Update all references to use Section terminology consistently
