# Results Export & Certificate Generation — Design Document

**Date:** 2026-03-29
**Status:** Approved

---

## Overview

Two tightly coupled features:

1. **Results Export** — Export student exam and project scores as an Excel workbook, scoped by section, class, or course.
2. **Certificate Generation** — Generate verifiable PDF certificates for passing students, with QR code linking to a public verification page.

---

## Feature 1: Results Export

### Scope Levels

| Scope | What it covers | Who can use |
|-------|---------------|-------------|
| Section | All students in a section, all courses for that section | TENANT_ADMIN, Coordinator |
| Class | All students in a class (across sections), all courses | TENANT_ADMIN, Coordinator |
| Course | All students enrolled in a specific course | TENANT_ADMIN |

### Data Sources

Two score sources in the system:

1. **Exams/Quizzes** → `student.assessment_submissions` table
   - Fields: `score` (BigDecimal), `maxScore`, `percentage`, `isPassed`
   - Linked to `content.assessments` for title, type (QUIZ, EXAM, ASSIGNMENT, etc.)

2. **Projects** → `student.project_event_grade` table
   - Fields: `marks` (Integer) per event
   - Linked to `student.project_event` for event name, `maxMarks`
   - Project total = sum of event grades; max = sum of event maxMarks

### Excel Structure

**Format:** `.xlsx` using Apache POI

**Sheet 1: Summary**

| Student Name | Email | Course 1 Total | Course 1 % | Course 2 Total | Course 2 % | Grand Total | Overall % |
|---|---|---|---|---|---|---|---|

**Per-course detail sheets (one sheet per course):**

| Student Name | Email | Exam 1 (Score/Max) | Exam 2 | ... | Project Total (Score/Max) | Event 1 | Event 2 | ... | Course Total | Course % |
|---|---|---|---|---|---|---|---|---|---|---|

- Exam columns: one per assessment (QUIZ, EXAM, ASSIGNMENT types)
- Project columns: total + individual event breakdowns
- Course total: sum of all exam scores + project total
- Course %: (course total / course max) × 100

### API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/results/export?sectionId={id}` | Export for a section |
| `GET` | `/api/results/export?classId={id}` | Export for a class |
| `GET` | `/api/results/export?courseId={id}` | Export for a course |

Returns `.xlsx` file download. Admin and Coordinator only.

### Architecture

- **Service:** `ResultsExportService` in the student module
- **Cross-service call:** Needs to fetch assessment details (title, type, maxScore) from the content service via REST call (assessments live in content schema)
- **Library:** Apache POI for Excel generation
- **Frontend:** "Export Results" button on section/class detail pages + a dedicated `/results` page with scope picker

---

## Feature 2: Certificate Generation

### Flow

```
1. Admin exports results Excel → Reviews in Excel
   → Applies custom logic (best of N, manual adjustments)
   → Creates CSV of passing students

2. Admin goes to Certificates page
   → Selects scope (section/class/course)
   → Uploads CSV (columns: name, email)
   → Selects certificate template
   → Previews sample certificate
   → Clicks "Generate"

3. Backend processes each student:
   → Validates enrollment
   → Generates unique credentialId (EDU-{YEAR}-{RANDOM5})
   → Snapshots scores into metadata
   → Renders PDF (background + text fields + QR code)
   → Uploads PDF to Azure Blob Storage
   → Creates Certificate record

4. Admin can download all PDFs as ZIP, download individual, or revoke
```

### Bulk Limit

If student count > 150, only SYSTEM_ADMIN can trigger bulk generation. TENANT_ADMIN/Coordinator see: "Contact system admin for bulk generation."

### Upload CSV Format

```csv
name,email
John Doe,john@example.com
Jane Smith,jane@example.com
```

System resolves by email. Name is for human validation.

### Certificate = One Per Student Per Course

If a student has 3 courses, they get 3 separate certificates.

---

## Data Model

### Schema: `certificate` (in student service)

### `certificate.certificate_templates`

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR (ULID) | Primary key |
| client_id | UUID | Tenant (null = system-wide default) |
| name | VARCHAR(255) | Template name |
| description | TEXT | Description |
| config | JSONB | Field positions, fonts, colors, text |
| background_image_url | VARCHAR(500) | Azure Blob URL |
| is_default | BOOLEAN | System-provided default |
| is_active | BOOLEAN | Soft delete |
| created_at | TIMESTAMPTZ | Audit |
| updated_at | TIMESTAMPTZ | Audit |

**Config JSON structure:**
```json
{
  "fields": [
    { "type": "studentName", "x": 400, "y": 300, "fontSize": 36, "fontWeight": "bold", "color": "#1E3A5F" },
    { "type": "courseName", "x": 400, "y": 380, "fontSize": 20 },
    { "type": "date", "x": 400, "y": 440, "fontSize": 14, "format": "MMMM dd, yyyy" },
    { "type": "credentialId", "x": 50, "y": 550, "fontSize": 10 },
    { "type": "qrCode", "x": 650, "y": 480, "size": 100 },
    { "type": "customText", "x": 400, "y": 250, "text": "Certificate of Completion", "fontSize": 28 },
    { "type": "image", "x": 180, "y": 470, "width": 120, "height": 50, "imageUrl": "https://..." },
    { "type": "logo", "x": 350, "y": 50, "width": 100, "height": 80, "imageUrl": "https://..." }
  ],
  "pageSize": { "width": 842, "height": 595 },
  "orientation": "landscape"
}
```

**Supported field types:** `studentName`, `courseName`, `date`, `credentialId`, `qrCode`, `customText`, `image`, `logo`

This structure is future-proof for the v2 drag-and-drop template builder. For v1, we provide 2-3 pre-built configs.

**Future (v2):** Template export/import as ZIP file containing `template.json` + `assets/` folder with images. Shared template library across tenants.

### `certificate.certificates`

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR (ULID) | Primary key |
| client_id | UUID | Tenant |
| student_id | VARCHAR | Student user ID |
| course_id | VARCHAR | Course |
| section_id | VARCHAR | Section context (nullable) |
| class_id | VARCHAR | Class context (nullable) |
| template_id | VARCHAR | FK to certificate_templates |
| credential_id | VARCHAR(20) | Unique short ID (EDU-2026-A3K9X) |
| qr_code_url | VARCHAR(500) | Public verification URL |
| pdf_url | VARCHAR(500) | Azure Blob URL of generated PDF |
| issued_at | TIMESTAMPTZ | Issue date |
| issued_by | VARCHAR | Admin user ID |
| revoked_at | TIMESTAMPTZ | Null unless revoked |
| revoked_reason | TEXT | Reason for revocation |
| metadata | JSONB | Scores snapshot, notes |
| is_active | BOOLEAN | Soft delete |
| created_at | TIMESTAMPTZ | Audit |
| updated_at | TIMESTAMPTZ | Audit |

### `certificate.certificate_visibility`

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR (ULID) | Primary key |
| client_id | UUID | Tenant |
| student_id | VARCHAR | Student |
| certificate_id | VARCHAR | FK to certificates |
| show_scores | BOOLEAN | Show individual exam scores (default: true) |
| show_project_details | BOOLEAN | Show project event scores (default: true) |
| show_overall_percentage | BOOLEAN | Show overall % (default: true) |
| show_course_name | BOOLEAN | Show course name (default: true) |

---

## API Endpoints

### Results Export

| Method | Path | Description | Who |
|--------|------|-------------|-----|
| `GET` | `/api/results/export?sectionId=` | Export section results | Admin, Coordinator |
| `GET` | `/api/results/export?classId=` | Export class results | Admin, Coordinator |
| `GET` | `/api/results/export?courseId=` | Export course results | Admin |

### Certificate Templates

| Method | Path | Description | Who |
|--------|------|-------------|-----|
| `GET` | `/api/certificates/templates` | List templates | Admin |
| `POST` | `/api/certificates/templates` | Create/customize template | Admin |
| `PUT` | `/api/certificates/templates/{id}` | Update template | Admin |
| `GET` | `/api/certificates/templates/{id}/preview` | Preview with sample data | Admin |

### Certificate Generation & Management

| Method | Path | Description | Who |
|--------|------|-------------|-----|
| `POST` | `/api/certificates/generate` | Upload CSV + generate certs | Admin (>150: SYSTEM_ADMIN only) |
| `GET` | `/api/certificates?sectionId=&courseId=` | List issued certificates | Admin |
| `GET` | `/api/certificates/{id}/download` | Download individual PDF | Admin, Student (own) |
| `GET` | `/api/certificates/download-all?sectionId=&courseId=` | Download all as ZIP | Admin |
| `POST` | `/api/certificates/{id}/revoke` | Revoke a certificate | Admin |

### Student-Facing

| Method | Path | Description | Who |
|--------|------|-------------|-----|
| `GET` | `/api/certificates/my` | Student's own certificates | Student |
| `PUT` | `/api/certificates/{id}/visibility` | Update visibility settings | Student |

### Public Verification

| Method | Path | Description | Who |
|--------|------|-------------|-----|
| `GET` | `/api/verify/{credentialId}` | Verification data | Public (no auth) |

---

## Public Verification Page

**URL:** `student-portal.vercel.app/verify/{credentialId}`

**Layout:**
- Institution logo + name
- Certificate status: Valid ✅ or Revoked ❌
- Student name, course name, issued date, credential ID
- Scores section (controlled by student visibility settings)
- Download PDF button
- For revoked: shows revocation reason and date

**No authentication required.** Clean, professional, mobile-responsive page.

---

## Frontend

### Admin Dashboard

- **Results page** (`/results`): Scope picker (section/class/course dropdown) + Export button
- **Certificates page** (`/certificates`): Template selection, CSV upload, generation, list of issued certificates with download/revoke actions
- **Section/Class detail pages**: "Export Results" quick action button

### Student Portal

- **My Certificates** page (`/certificates`): List of certificates with download + visibility settings toggles
- **Verification page** (`/verify/[credentialId]`): Public page, no auth

---

## Tech Stack

- **Excel generation:** Apache POI (Java)
- **PDF generation:** Apache PDFBox or iText (Java)
- **QR code generation:** ZXing (com.google.zxing)
- **Storage:** Azure Blob Storage (PDFs, template assets)
- **Frontend:** Next.js pages, shadcn/ui components

---

## Gateway Routes

```yaml
- id: results-export
  uri: ${CORE_API_SERVICE_URL}
  predicates:
    - Path=/api/results/**

- id: certificates
  uri: ${CORE_API_SERVICE_URL}
  predicates:
    - Path=/api/certificates/**

- id: certificate-verification
  uri: ${CORE_API_SERVICE_URL}
  predicates:
    - Path=/api/verify/**
```

---

## Audit Logging

| Action | Entity | Metadata |
|--------|--------|----------|
| Export results | ResultsExport | scope, studentCount, courseCount, exportedBy |
| Generate certificates | Certificate | scope, studentCount, templateId, generatedBy |
| Revoke certificate | Certificate | credentialId, reason, revokedBy |
| Update visibility | CertificateVisibility | certificateId, changes, studentId |
