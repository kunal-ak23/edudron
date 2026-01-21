# Course Book PDF (Content Service)

This module adds **manual** course-book PDF generation for an already-created course.

## Endpoints

### Regenerate + store (Azure Blob + CourseResource)

`POST /content/courses/{courseId}/book.pdf/regenerate`

- Generates the PDF
- Uploads it to Azure Blob Storage
- Saves/updates a `CourseResource` row with `resourceType=PDF` and `fileUrl=<blob url>`
- Returns the PDF bytes (`application/pdf`)

Example:

```bash
curl -L -X POST \
  -H "Authorization: Bearer <jwt>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8082/content/courses/<courseId>/book.pdf/regenerate" \
  --output course-book.pdf
```

### Download stored PDF (no auto-regenerate)

`GET /content/courses/{courseId}/book.pdf`

- Streams the stored blob (server-side)
- Returns **404** if the course book has not been generated yet

Example:

```bash
curl -L \
  -H "Authorization: Bearer <jwt>" \
  -H "X-Tenant-Id: <tenant-uuid>" \
  "http://localhost:8082/content/courses/<courseId>/book.pdf" \
  --output course-book.pdf
```

## Smoke test checklist

- Call regenerate endpoint and confirm:
  - Response is a valid PDF
  - A `CourseResource` (type PDF) exists for the course
  - `fileUrl` points to the uploaded blob
- Call download endpoint and confirm it returns the stored PDF
- Open the PDF and confirm:
  - Table of Contents entries have page numbers
  - Page numbers match the start pages of sections/lectures

