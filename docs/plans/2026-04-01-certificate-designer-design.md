# Certificate Designer — Design Document

**Date**: 2026-04-01  
**Status**: Approved

## Overview

A drag-and-drop visual certificate template designer using react-konva, with export/import as ZIP bundles (config + assets).

## Architecture

```
Admin Dashboard: /certificates/designer

┌──────────────┐  ┌────────────────────────────────┐
│  Field Panel  │  │     react-konva Canvas          │
│              │  │                                │
│ + Student    │  │   Drag/resize/rotate elements  │
│ + Course     │  │   on scaled A4 canvas          │
│ + Date       │  │                                │
│ + QR Code    │  │   A4 Landscape / Portrait      │
│ + Custom Text│  │                                │
│ + Custom Img │  │                                │
│ + Logo       │  │                                │
│ + Signature  │  │                                │
│ + Grade      │  │                                │
│ + Institute  │  │                                │
│ + Credential │  │                                │
├──────────────┤  ├────────────────────────────────┤
│  Properties  │  │  Toolbar: Undo/Redo, Zoom,     │
│  Panel       │  │  Grid snap, Page size/orient,  │
│ (font, color,│  │  Export, Import, Save           │
│  size, align)│  └────────────────────────────────┘
└──────────────┘
```

## Field Types

| Type | Drag Behavior | Properties |
|------|--------------|------------|
| `studentName` | Text placeholder "Student Name" | font, size, weight, color, alignment |
| `courseName` | Text placeholder "Course Name" | font, size, weight, color, alignment |
| `date` | Text placeholder "March 31, 2026" | font, size, color, format string |
| `credentialId` | Text placeholder "EDU-2026-XXXXX" | font, size, color |
| `instituteName` | Text placeholder "University Name" | font, size, weight, color |
| `grade` | Text placeholder "Grade: A+" | font, size, color |
| `qrCode` | QR code square | size (width = height) |
| `customText` | Editable text | font, size, weight, color, alignment, text content |
| `customImage` | Image upload | width, height, opacity |
| `logo` | Image upload (institute logo) | width, height |
| `signature` | Image upload (signature) | width, height, label text below |
| `backgroundImage` | Full canvas background | opacity |

## Canvas (react-konva)

- **Stage** scaled to fit viewport, actual dimensions match A4 (842x595 landscape, 595x842 portrait)
- **Transformer** on selected elements — drag handles for resize, rotation
- **Snap-to-grid** optional toggle, 10px grid
- **Undo/Redo** via state history stack (max 50 states)
- **Zoom** controls: fit, 50%, 75%, 100%, 150%
- **Page sizes**: A4 landscape (842x595), A4 portrait (595x842)

## Data Model

No schema changes — uses existing `certificate_templates.config` JSONB column:

```json
{
  "fields": [
    { "type": "studentName", "x": 421, "y": 260, "fontSize": 36, "fontWeight": "bold", "color": "#1E3A5F", "rotation": 0 },
    { "type": "customImage", "x": 100, "y": 50, "width": 150, "height": 80, "imageUrl": "https://..." },
    { "type": "qrCode", "x": 700, "y": 460, "size": 100 },
    { "type": "backgroundImage", "imageUrl": "https://...", "opacity": 1.0 }
  ],
  "pageSize": { "width": 842, "height": 595 },
  "orientation": "landscape",
  "backgroundColor": "#FFFFFF"
}
```

## Export/Import (ZIP Bundle)

### Export
1. Collect template config JSON
2. Download all referenced images (backgroundImage, customImage, logo, signature)
3. Package as ZIP: `template.json` + `assets/` folder with images
4. Download as `{template-name}.zip`

### Import
1. User uploads `.zip` file
2. Extract `template.json` + `assets/` folder
3. Upload assets to Azure Blob Storage under tenant's media folder
4. Replace local asset paths in config with new blob URLs
5. Create new template with updated config

## Backend Changes

### New endpoints on CertificateTemplateController
- `POST /api/certificates/templates/{id}/export` — returns ZIP file
- `POST /api/certificates/templates/import` — accepts ZIP multipart, creates template
- `PUT /api/certificates/templates/{id}` — update existing template config (for save)
- `POST /api/certificates/templates` — create new template (for save-as-new)

### CertificateTemplateService additions
- `exportTemplate(id)` — build ZIP with config JSON + download and bundle referenced images
- `importTemplate(zipFile, clientId)` — extract ZIP, re-upload assets to blob storage, create template
- `updateTemplate(id, config)` — update existing template config
- `createTemplate(name, description, config)` — create new tenant-scoped template

## Frontend Pages

| Page | Route | Purpose |
|------|-------|---------|
| Template list | `/certificates` (existing, add "Templates" tab) | List templates, create/edit/delete |
| Designer | `/certificates/designer?id={templateId}` | Edit existing template |
| Designer (new) | `/certificates/designer` | Create new template from blank |

## Frontend Components

### CertificateDesigner (main page component)
- Manages state: fields array, selected field, history stack
- Renders FieldPanel, Canvas, PropertiesPanel, Toolbar

### DesignerCanvas
- react-konva Stage + Layer
- Renders each field as a Konva node (Text, Image, Rect for QR placeholder)
- Handles selection, drag, resize via Transformer
- Grid overlay when snap enabled

### FieldPanel
- List of draggable field type buttons
- Click to add to canvas center, or drag onto canvas

### PropertiesPanel
- Context-sensitive: shows properties for selected field
- Font family, size, weight, color picker, alignment
- Position (x, y), size (width, height), rotation
- Image upload for image-type fields
- Delete field button

### DesignerToolbar
- Undo / Redo buttons
- Zoom controls
- Page size toggle (landscape/portrait)
- Grid snap toggle
- Save button
- Export / Import buttons

## Tech Stack
- **Canvas**: react-konva (konva + react-konva packages)
- **Color picker**: existing shadcn/ui or simple hex input
- **ZIP handling**: JSZip (frontend for export preview), backend for actual export/import
- **File upload**: existing MediaApi for image uploads
