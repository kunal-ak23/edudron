# Section Activation Feature

## Overview
Implemented the ability to activate inactive sections from the admin dashboard. Previously, sections could only be deactivated, with no way to reactivate them.

## Changes Made

### Backend Changes

#### 1. SectionService.java
- **Added**: `activateSection(String sectionId)` method
  - Validates tenant context
  - Finds the section by ID and client ID
  - Validates that the parent class is still active before allowing activation
  - Sets `isActive = true` and saves the section
  - Throws appropriate exceptions if:
    - Section not found
    - Parent class is inactive

#### 2. SectionController.java
- **Added**: `PUT /api/sections/{id}/activate` endpoint
  - Activates an inactive section
  - Returns 204 No Content on success
  - Includes OpenAPI documentation

### Frontend Changes

#### 1. SectionsApi (shared-utils)
- **Added**: `activateSection(id: string)` method
  - Calls `PUT /api/sections/{id}/activate`
  - Returns a Promise<void>

#### 2. Section Detail Page
- **Added**: State management for activation dialog
  - `showActivateDialog` state variable
  - `handleActivate()` function to call the API and reload section data

- **Updated**: Section header
  - Shows "Inactive" badge when section is not active
  - Badge is displayed in yellow with appropriate styling

- **Updated**: Form fields
  - All input fields are disabled when section is inactive
  - Prevents editing of inactive sections

- **Updated**: Action buttons
  - Shows "Deactivate Section" button (destructive) when section is active
  - Shows "Activate Section" button (primary) when section is inactive
  - "Save Changes" button is disabled when section is inactive

- **Updated**: Deactivate handler
  - Now reloads the section after deactivation instead of navigating away
  - This allows users to immediately see the inactive state and activate if needed

- **Added**: Activation confirmation dialog
  - Confirms user intent before activating the section
  - Uses the same ConfirmationDialog component as deactivation

## User Experience Flow

### When Section is Active:
1. User sees all form fields enabled
2. User can edit section details
3. User can deactivate the section via the "Deactivate Section" button
4. Upon deactivation, the page refreshes showing the inactive state

### When Section is Inactive:
1. User sees an "Inactive" badge in the section header
2. All form fields are disabled (read-only)
3. "Save Changes" button is disabled
4. User sees an "Activate Section" button instead of "Deactivate Section"
5. Clicking "Activate Section" shows a confirmation dialog
6. Upon activation, the page refreshes and all controls become enabled again

## Validation & Safety

1. **Backend Validation**:
   - Cannot activate a section if its parent class is inactive
   - Returns appropriate error message: "Cannot activate section: class is not active"

2. **UI Feedback**:
   - Toast notifications for success and error cases
   - Clear visual indication (badge) when section is inactive
   - Confirmation dialogs prevent accidental activation/deactivation

3. **Permission Context**:
   - All operations respect tenant context
   - Only sections belonging to the current tenant can be modified

## Testing Recommendations

1. **Test activation of inactive section**:
   - Deactivate a section
   - Verify "Inactive" badge appears
   - Verify form fields are disabled
   - Click "Activate Section"
   - Confirm in dialog
   - Verify section becomes active and form fields are enabled

2. **Test activation with inactive parent class**:
   - Deactivate a class
   - Deactivate one of its sections
   - Try to activate the section
   - Verify error message appears

3. **Test activation permissions**:
   - As different tenant users
   - Verify users can only activate sections in their own tenant

## Related Files

### Backend
- `student/src/main/java/com/datagami/edudron/student/service/SectionService.java`
- `student/src/main/java/com/datagami/edudron/student/web/SectionController.java`

### Frontend
- `frontend/packages/shared-utils/src/api/sections.ts`
- `frontend/apps/admin-dashboard/src/app/sections/[id]/page.tsx`

## API Documentation

### Activate Section
```
PUT /api/sections/{id}/activate
```

**Description**: Activate an inactive section

**Path Parameters**:
- `id` (string, required): Section ID (ULID)

**Response**:
- `204 No Content`: Section activated successfully
- `400 Bad Request`: Validation error (e.g., class is inactive)
- `404 Not Found`: Section not found or doesn't belong to tenant

**Example Error Responses**:
```json
{
  "message": "Cannot activate section: class is not active"
}
```

```json
{
  "message": "Section not found: 01HXXX..."
}
```
