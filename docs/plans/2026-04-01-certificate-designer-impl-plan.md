# Certificate Designer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a drag-and-drop visual certificate template designer with react-konva, including export/import as ZIP bundles.

**Architecture:** react-konva canvas in the admin dashboard with field palette, properties panel, and toolbar. Backend export/import endpoints on CertificateTemplateController. Template config stored in existing JSONB `config` column.

**Tech Stack:** react-konva, konva, JSZip (frontend), existing Spring Boot + Azure Blob Storage (backend)

---

### Task 1: Install dependencies and create designer page shell

**Files:**
- Modify: `frontend/apps/admin-dashboard/package.json`
- Create: `frontend/apps/admin-dashboard/src/app/certificates/designer/page.tsx`
- Modify: `frontend/apps/admin-dashboard/src/app/certificates/page.tsx` (add "Design" button)

**Step 1: Install react-konva and dependencies**

```bash
cd frontend/apps/admin-dashboard
npm install konva react-konva jszip @types/jszip
```

**Step 2: Create designer page shell**

Create `frontend/apps/admin-dashboard/src/app/certificates/designer/page.tsx` with:
- `'use client'` directive
- Basic layout: left sidebar (field palette), center (canvas placeholder), toolbar
- URL params: `?id={templateId}` for editing, no id = new template
- Load template config from API if `id` provided
- Use `useRequireAuth({ allowedRoles: ['TENANT_ADMIN', 'SYSTEM_ADMIN', 'CONTENT_MANAGER'] })`

**Step 3: Add "Design Template" button to certificates page**

In `certificates/page.tsx`, add a button/link that navigates to `/certificates/designer` for new templates and `/certificates/designer?id={id}` for editing existing ones.

**Step 4: Commit**

```bash
git commit -m "feat: scaffold certificate designer page with react-konva dependencies"
```

---

### Task 2: Build the DesignerCanvas component

**Files:**
- Create: `frontend/apps/admin-dashboard/src/components/certificate-designer/DesignerCanvas.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/certificate-designer/types.ts`

**Step 1: Define types**

Create `types.ts` with:

```typescript
export type FieldType = 
  | 'studentName' | 'courseName' | 'date' | 'credentialId' 
  | 'instituteName' | 'grade' | 'qrCode' | 'customText' 
  | 'customImage' | 'logo' | 'signature' | 'backgroundImage'

export interface DesignerField {
  id: string           // unique ID for this instance (nanoid)
  type: FieldType
  x: number
  y: number
  width?: number
  height?: number
  rotation?: number
  // Text properties
  text?: string        // for customText
  fontSize?: number
  fontWeight?: 'normal' | 'bold'
  color?: string
  alignment?: 'left' | 'center' | 'right'
  format?: string      // for date format
  // Image properties
  imageUrl?: string
  opacity?: number
  // QR properties
  size?: number        // for qrCode (width = height)
  // Label
  label?: string       // for signature label
}

export interface TemplateConfig {
  fields: DesignerField[]
  pageSize: { width: number; height: number }
  orientation: 'landscape' | 'portrait'
  backgroundColor?: string
}

export const PAGE_SIZES = {
  'a4-landscape': { width: 842, height: 595 },
  'a4-portrait': { width: 595, height: 842 },
} as const

export const FIELD_DEFAULTS: Record<FieldType, Partial<DesignerField>> = {
  studentName: { text: 'Student Name', fontSize: 36, fontWeight: 'bold', color: '#1E3A5F', width: 300, height: 50 },
  courseName: { text: 'Course Name', fontSize: 22, fontWeight: 'bold', color: '#0891B2', width: 300, height: 40 },
  date: { text: 'April 01, 2026', fontSize: 14, color: '#666666', format: 'MMMM dd, yyyy', width: 200, height: 30 },
  credentialId: { text: 'EDU-2026-XXXXX', fontSize: 10, color: '#999999', width: 180, height: 25 },
  instituteName: { text: 'University Name', fontSize: 18, fontWeight: 'bold', color: '#1E3A5F', width: 250, height: 35 },
  grade: { text: 'Grade: A+', fontSize: 16, color: '#333333', width: 150, height: 30 },
  qrCode: { size: 100, width: 100, height: 100 },
  customText: { text: 'Custom Text', fontSize: 16, color: '#333333', width: 200, height: 30 },
  customImage: { width: 150, height: 100, opacity: 1 },
  logo: { width: 120, height: 80, opacity: 1 },
  signature: { width: 150, height: 60, opacity: 1, label: 'Authorized Signature' },
  backgroundImage: { opacity: 1 },
}
```

**Step 2: Build DesignerCanvas**

Create `DesignerCanvas.tsx` using react-konva:
- `Stage` with dimensions matching the page size, scaled to fit viewport
- `Layer` with grid lines (optional, toggled by prop)
- Render each `DesignerField` as appropriate Konva node:
  - Text fields → `<Text>` node with the placeholder text
  - Image fields → `<Image>` node (load from URL or show placeholder rect)
  - QR code → `<Rect>` with "QR" label placeholder
  - Background image → `<Image>` at (0,0) spanning full canvas
- `<Transformer>` attached to selected node for resize/rotate
- Handle `onDragEnd` to update field position
- Handle `onTransformEnd` to update field size/rotation
- Handle click on empty space to deselect

**Step 3: Commit**

```bash
git commit -m "feat: add DesignerCanvas component with react-konva rendering"
```

---

### Task 3: Build the FieldPanel component (left sidebar)

**Files:**
- Create: `frontend/apps/admin-dashboard/src/components/certificate-designer/FieldPanel.tsx`

**Step 1: Build FieldPanel**

A sidebar panel listing all available field types as buttons. Clicking a button adds that field to the canvas center.

- Group fields into sections: "Data Fields" (studentName, courseName, date, credentialId, instituteName, grade), "Media" (qrCode, customImage, logo, signature, backgroundImage), "Custom" (customText)
- Each button shows icon + label
- On click: call `onAddField(fieldType)` callback which creates a new `DesignerField` with defaults from `FIELD_DEFAULTS` and position at canvas center
- Generate unique ID with `crypto.randomUUID()` or nanoid

**Step 2: Commit**

```bash
git commit -m "feat: add FieldPanel component for certificate designer"
```

---

### Task 4: Build the PropertiesPanel component (right sidebar)

**Files:**
- Create: `frontend/apps/admin-dashboard/src/components/certificate-designer/PropertiesPanel.tsx`

**Step 1: Build PropertiesPanel**

Context-sensitive panel that shows properties for the selected field. Uses shadcn/ui form elements.

- **When no field selected:** Show "Select an element to edit its properties"
- **For text fields** (studentName, courseName, date, credentialId, instituteName, grade, customText):
  - Text content input (editable for customText only, read-only label for data fields)
  - Font size slider/input (8-72)
  - Font weight toggle (normal/bold)
  - Color picker (hex input + color swatch)
  - Text alignment (left/center/right buttons)
  - Position X, Y inputs
  - Rotation input
  - Date format input (for date type only)
- **For image fields** (customImage, logo, signature):
  - Image upload button (uses existing MediaApi)
  - Width, height inputs
  - Opacity slider (0-1)
  - Position X, Y
  - Rotation
  - Label input (for signature)
- **For QR code:**
  - Size input (width = height)
  - Position X, Y
- **For background image:**
  - Image upload button
  - Opacity slider
- **Delete field** button at bottom (red, with confirm)

All changes call `onUpdateField(fieldId, updates)` callback.

**Step 2: Commit**

```bash
git commit -m "feat: add PropertiesPanel component for certificate designer"
```

---

### Task 5: Build the DesignerToolbar component

**Files:**
- Create: `frontend/apps/admin-dashboard/src/components/certificate-designer/DesignerToolbar.tsx`

**Step 1: Build DesignerToolbar**

Horizontal toolbar above the canvas:

- **Undo** / **Redo** buttons (disabled when history empty)
- **Zoom** controls: dropdown with Fit, 50%, 75%, 100%, 150%
- **Page size** toggle: Landscape / Portrait buttons
- **Grid snap** toggle checkbox
- **Background color** picker
- **Save** button (primary)
- **Save as New** button (secondary, when editing existing)
- **Export** button → triggers ZIP download
- **Import** button → file input for ZIP upload

**Step 2: Commit**

```bash
git commit -m "feat: add DesignerToolbar component for certificate designer"
```

---

### Task 6: Wire up the designer page with state management

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/certificates/designer/page.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/certificate-designer/useDesignerState.ts`

**Step 1: Create useDesignerState hook**

Custom hook managing all designer state:

```typescript
function useDesignerState(initialConfig?: TemplateConfig) {
  // State
  const [fields, setFields] = useState<DesignerField[]>([])
  const [selectedFieldId, setSelectedFieldId] = useState<string | null>(null)
  const [pageSize, setPageSize] = useState(PAGE_SIZES['a4-landscape'])
  const [orientation, setOrientation] = useState<'landscape' | 'portrait'>('landscape')
  const [backgroundColor, setBackgroundColor] = useState('#FFFFFF')
  const [zoom, setZoom] = useState(1)
  const [snapToGrid, setSnapToGrid] = useState(false)
  
  // History for undo/redo
  const [history, setHistory] = useState<DesignerField[][]>([])
  const [historyIndex, setHistoryIndex] = useState(-1)
  
  // Actions
  const addField = (type: FieldType) => { ... }
  const updateField = (id: string, updates: Partial<DesignerField>) => { ... }
  const deleteField = (id: string) => { ... }
  const undo = () => { ... }
  const redo = () => { ... }
  const toggleOrientation = () => { ... }
  
  // Convert to/from config format
  const toConfig = (): TemplateConfig => { ... }
  const loadConfig = (config: TemplateConfig) => { ... }
  
  return { fields, selectedFieldId, setSelectedFieldId, pageSize, orientation,
           backgroundColor, zoom, snapToGrid, addField, updateField, deleteField,
           undo, redo, canUndo, canRedo, toggleOrientation, setZoom, setSnapToGrid,
           setBackgroundColor, toConfig, loadConfig }
}
```

**Step 2: Wire up designer page**

Connect all components:
- Load template from API on mount (if `?id=` present)
- FieldPanel → `addField`
- DesignerCanvas → `selectedFieldId`, `setSelectedFieldId`, `updateField`, fields
- PropertiesPanel → selected field, `updateField`, `deleteField`
- DesignerToolbar → undo/redo, zoom, orientation, save, export/import
- Save button → convert to config with `toConfig()`, call `certificatesApi.updateTemplate(id, { config })`

**Step 3: Commit**

```bash
git commit -m "feat: wire up certificate designer with state management"
```

---

### Task 7: Backend — Export/Import endpoints

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/web/CertificateTemplateController.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/service/CertificateTemplateService.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/service/MediaUploadHelper.java`

**Step 1: Add export endpoint**

Add to `CertificateTemplateController`:
```java
@GetMapping("/{id}/export")
@Operation(summary = "Export template as ZIP")
public ResponseEntity<byte[]> exportTemplate(@PathVariable String id)
```

In `CertificateTemplateService`:
- Load template by ID + clientId check
- Parse config JSON, find all image URLs (backgroundImageUrl, customImage, logo, signature imageUrl fields)
- Download each image from blob storage as byte[]
- Build ZIP: `template.json` (config + name + description) + `assets/` folder with images
- In template.json, replace absolute blob URLs with relative paths (`assets/filename.png`)
- Return ZIP bytes

**Step 2: Add import endpoint**

Add to `CertificateTemplateController`:
```java
@PostMapping("/import")
@Operation(summary = "Import template from ZIP")
public ResponseEntity<CertificateTemplateDTO> importTemplate(@RequestParam("file") MultipartFile file)
```

In `CertificateTemplateService`:
- Validate ZIP structure (must contain `template.json`)
- Extract `template.json` → parse name, description, config
- Extract `assets/` files
- Upload each asset to Azure Blob Storage via `MediaUploadHelper`
- Replace relative paths in config with new blob URLs
- Create new template with tenant's clientId
- Return created template DTO

**Step 3: Add role checks on both endpoints**

**Step 4: Commit**

```bash
git commit -m "feat: add export/import endpoints for certificate templates"
```

---

### Task 8: Frontend — Export/Import integration

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/certificates.ts`
- Modify: `frontend/apps/admin-dashboard/src/components/certificate-designer/DesignerToolbar.tsx`

**Step 1: Add API methods**

In `certificates.ts`:
```typescript
async exportTemplate(id: string): Promise<Blob> {
  return this.apiClient.downloadFile(`/api/certificates/templates/${id}/export`)
}

async importTemplate(file: File): Promise<CertificateTemplate> {
  return this.apiClient.postForm('/api/certificates/templates/import', { file })
}
```

**Step 2: Wire export button**

In DesignerToolbar:
- Export: call `certificatesApi.exportTemplate(templateId)`, trigger browser download of ZIP blob
- Import: file input accepting `.zip`, call `certificatesApi.importTemplate(file)`, then `loadConfig()` with the returned template's config

**Step 3: Publish shared-utils**

```bash
cd frontend/packages/shared-utils && npm version patch && npm run build
GITHUB_TOKEN=... bash ../publish.sh shared-utils
# Update both apps to new version
```

**Step 4: Commit**

```bash
git commit -m "feat: add export/import integration for certificate designer"
```

---

### Task 9: Add template list/management to certificates page

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/certificates/page.tsx`

**Step 1: Add Templates tab**

Add a "Templates" tab alongside existing "Generate" and "Certificates" tabs:
- List all templates (from `certificatesApi.listTemplates()`)
- Each template card shows: name, description, "Default" badge if isDefault, preview thumbnail
- Actions per template: Edit (→ designer), Export, Delete
- "Create New Template" button → `/certificates/designer`
- "Import Template" button → file upload

**Step 2: Commit**

```bash
git commit -m "feat: add template management tab to certificates page"
```

---

### Task 10: Testing and polish

**Files:**
- Various component files for fixes

**Step 1: Test full flow**

1. Navigate to Certificates → Templates tab
2. Click "Create New Template"
3. Add fields: studentName, courseName, date, qrCode, customText
4. Drag/resize fields on canvas
5. Change properties (font, color, size)
6. Toggle landscape/portrait
7. Save template
8. Export as ZIP
9. Delete the template
10. Import the ZIP → verify template recreated with same layout
11. Generate a certificate using the custom template
12. Verify PDF matches the designer layout

**Step 2: Fix any issues found**

**Step 3: Commit and push**

```bash
git commit -m "fix: polish certificate designer after testing"
```

---

## Dependency Graph

```
Task 1 (shell + deps)
  ├── Task 2 (canvas)
  ├── Task 3 (field panel)
  ├── Task 4 (properties panel)
  ├── Task 5 (toolbar)
  └── Task 7 (backend export/import)
       └── Task 8 (frontend export/import)

Task 2,3,4,5 → Task 6 (wire up)
Task 6 + Task 8 → Task 9 (template management)
Task 9 → Task 10 (testing)
```

Tasks 2, 3, 4, 5, 7 can be done in **parallel**.
