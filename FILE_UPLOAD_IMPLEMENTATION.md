# File Upload Implementation

## Overview

File upload functionality has been implemented similar to Gulliyo, allowing users to upload files directly to Azure Storage instead of providing URLs. The system automatically handles file uploads and generates URLs after uploading to Azure Storage.

## Backend Implementation

### 1. MediaFolderConstants
**Location**: `content/src/main/java/com/datagami/edudron/content/constants/MediaFolderConstants.java`

Defines folder constants for organizing uploads:
- `COURSES` - Course-related files
- `THUMBNAILS` - Course thumbnail images
- `VIDEOS` - Video files
- `PREVIEW_VIDEOS` - Course preview videos
- `LECTURES` - Lecture content
- `ASSESSMENTS` - Assessment files
- `RESOURCES` - Course resources
- `INSTRUCTORS` - Instructor profile images
- `TEMP` - Temporary files

### 2. MediaUploadService
**Location**: `content/src/main/java/com/datagami/edudron/content/service/MediaUploadService.java`

Service for handling file uploads to Azure Storage:
- `uploadImage()` - Uploads image files (max 10MB)
- `uploadVideo()` - Uploads video files (max 500MB)
- `deleteMedia()` - Deletes media files from Azure Storage

**Features**:
- Validates file type and size
- Organizes files by tenant and folder structure: `{tenantId}/{folder}/{yyyy/MM/dd}/{uuid}.{ext}`
- Automatically creates container if it doesn't exist
- Returns public URL after upload

### 3. MediaUploadController
**Location**: `content/src/main/java/com/datagami/edudron/content/web/MediaUploadController.java`

REST endpoints for file uploads:
- `POST /api/content/media/upload/image` - Upload image
- `POST /api/content/media/upload/video` - Upload video
- `DELETE /api/content/media/delete` - Delete media by URL

**Request Parameters**:
- `file` (MultipartFile) - The file to upload
- `folder` (String, optional) - Folder name (default: 'thumbnails' for images, 'preview-videos' for videos)

**Response**:
```json
{
  "url": "https://account.blob.core.windows.net/container/tenant/folder/2024/01/01/uuid.jpg",
  "message": "Image uploaded successfully",
  "tenantId": "tenant-uuid",
  "folder": "thumbnails"
}
```

## Frontend Implementation

### 1. FileUpload Component
**Location**: `frontend/packages/ui-components/src/components/FileUpload.tsx`

Reusable file upload component with:
- Drag and drop support
- Image preview
- File type and size validation
- Upload progress indicator
- Error handling

**Props**:
- `label` - Label for the upload field
- `accept` - Accepted file types (e.g., "image/*", "video/*")
- `maxSize` - Maximum file size in bytes
- `value` - Current URL (for preview)
- `onChange` - Callback when URL changes
- `onUpload` - Async function that uploads file and returns URL
- `error` - Error message to display
- `helperText` - Helper text below the input

### 2. MediaApi
**Location**: `frontend/packages/shared-utils/src/api/media.ts`

API client for media uploads:
- `uploadImage(file, folder)` - Upload image file
- `uploadVideo(file, folder)` - Upload video file
- `deleteMedia(url)` - Delete media file

### 3. Course Edit Page
**Location**: `frontend/apps/admin-dashboard/src/app/courses/[id]/page.tsx`

Updated to use `FileUpload` components instead of URL inputs:
- Thumbnail image upload (images up to 10MB)
- Preview video upload (videos up to 500MB)

## Configuration

### Azure Storage Configuration

The Content service uses the same Azure Storage configuration as Gulliyo:

**application.yml**:
```yaml
azure:
  storage:
    connection-string: ${AZURE_STORAGE_CONNECTION_STRING:}
    account-name: ${AZURE_STORAGE_ACCOUNT_NAME:}
    container-name: ${AZURE_STORAGE_CONTAINER_NAME:edudron-media}
    base-url: ${AZURE_STORAGE_BASE_URL:}
```

**Environment Variables**:
- `AZURE_STORAGE_CONNECTION_STRING` - Azure Storage connection string
- `AZURE_STORAGE_ACCOUNT_NAME` - Azure Storage account name (for managed identity)
- `AZURE_STORAGE_CONTAINER_NAME` - Container name (default: `edudron-media`)
- `AZURE_STORAGE_BASE_URL` - Base URL for generated file URLs

## Usage

### Backend

1. **Upload Image**:
```java
@Autowired
private MediaUploadService mediaUploadService;

String imageUrl = mediaUploadService.uploadImage(file, "thumbnails", tenantId);
```

2. **Upload Video**:
```java
String videoUrl = mediaUploadService.uploadVideo(file, "preview-videos", tenantId);
```

### Frontend

1. **Using FileUpload Component**:
```tsx
import { FileUpload } from '@edudron/ui-components'
import { mediaApi } from '@/lib/api'

<FileUpload
  label="Thumbnail Image"
  accept="image/*"
  maxSize={10 * 1024 * 1024} // 10MB
  value={thumbnailUrl}
  onChange={(url) => setThumbnailUrl(url)}
  onUpload={async (file) => await mediaApi.uploadImage(file, 'thumbnails')}
/>
```

2. **Using MediaApi Directly**:
```tsx
import { mediaApi } from '@/lib/api'

const handleUpload = async (file: File) => {
  try {
    const url = await mediaApi.uploadImage(file, 'thumbnails')
    console.log('Uploaded URL:', url)
  } catch (error) {
    console.error('Upload failed:', error)
  }
}
```

## File Structure in Azure Storage

Files are organized in the following structure:
```
{container-name}/
  {tenant-id}/
    {folder}/
      {yyyy}/
        {MM}/
          {dd}/
            {uuid}.{ext}
```

**Example**:
```
edudron-media/
  123e4567-e89b-12d3-a456-426614174000/
    thumbnails/
      2024/
        01/
          15/
            a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg
```

## Security

- Files are organized by tenant ID for multi-tenancy
- File type validation (images must be image/*, videos must be video/*)
- File size limits (10MB for images, 500MB for videos)
- JWT authentication required for upload endpoints
- Tenant context automatically extracted from JWT token

## Dependencies

### Backend
- `com.azure:azure-storage-blob:12.23.1` - Azure Storage SDK
- `com.azure:azure-identity:1.10.4` - Azure Identity SDK

### Frontend
- Already included in existing dependencies (axios, React)

## Testing

1. **Test Image Upload**:
   - Navigate to course edit page
   - Click "Upload" on thumbnail field
   - Select an image file
   - Verify upload completes and URL is set

2. **Test Video Upload**:
   - Navigate to course edit page
   - Click "Upload" on preview video field
   - Select a video file
   - Verify upload completes and URL is set

3. **Test File Validation**:
   - Try uploading a file that's too large
   - Try uploading wrong file type
   - Verify error messages are displayed

## Notes

- The system uses the same Azure Storage credentials as Gulliyo
- Files are automatically organized by tenant and date
- URLs are generated automatically after upload
- The existing `MediaAssetService` and `MediaAssetController` remain for more complex use cases (database tracking, metadata, etc.)
- The new `MediaUploadService` is simpler and focused on direct upload-to-URL workflow


