# CLAUDE.md - Project Context & Findings

## Project Overview
EduDron is an educational platform with:
- **Admin Dashboard** (Next.js, port 3000) — `frontend/apps/admin-dashboard/`
- **Student Portal** (Next.js, port 3001) — `frontend/apps/student-portal/`
- **Backend** (Spring Boot, gateway port 8080, content service port 8082) — `backend/`
- **Shared Utils** (`@kunal-ak23/edudron-shared-utils`) — `frontend/packages/shared-utils/`

## Shared Utils Package
- Published to GitHub npm registry: `@kunal-ak23/edudron-shared-utils`
- Built with **tsup** (`npm run build` in `frontend/packages/shared-utils/`)
- Current version: **1.0.28**
- All imports from shared-utils MUST use the npm package path, NOT relative paths
- CSS exports use subpath: `@kunal-ak23/edudron-shared-utils/tiptap/editor-styles.css`
- `tsconfig.json` uses `"moduleResolution": "bundler"` to support subpath exports (e.g., `@tiptap/react/menus`)
- After making changes to shared-utils, always run `npm run build` before testing

## TipTap Editor Setup

### Editor Components
| Component | Location | Purpose |
|-----------|----------|---------|
| `TipTapMarkdownEditor` | `admin-dashboard/src/components/` | Markdown editor (uses `tiptap-markdown`) |
| `RichTextEditor` | `admin-dashboard/src/components/` | HTML editor (no markdown conversion) |
| `TipTapContentViewer` | `student-portal/src/components/` | Read-only viewer for student portal |
| `MarkdownRenderer` | Both apps `/src/components/` | Read-only markdown rendering |

### TipTap Extensions (shared-utils)
All custom TipTap extensions live in `frontend/packages/shared-utils/src/tiptap/`:

1. **`ResizableImage.ts`** — Custom Image extension extending `@tiptap/extension-image`
   - Adds `alignment` attribute (`'left' | 'center' | 'right'`, default `'center'`)
   - Enables TipTap's built-in resize via `ResizableNodeView` (corner drag handles)
   - Uses `width: fit-content` on the wrapper div for alignment margins to work
   - `renderMarkdown`: images with width/alignment serialize as `<img>` HTML tags; plain images use `![](url)` syntax
   - Command: `setImageAlignment(alignment)` — updates alignment via `updateAttributes`

2. **`ImageBubbleMenu.tsx`** — Floating toolbar for image alignment
   - Uses `BubbleMenu` from `@tiptap/react/menus` (NOT from `@tiptap/react` directly)
   - TipTap v3.20 uses **Floating UI** (NOT Tippy.js) — use `options={{ placement: 'top' }}` not `tippyOptions`
   - Shows 3 alignment buttons + image width in px
   - Active alignment highlighted with blue

3. **`HighlightMark.ts`** — Custom mark for text highlighting (used in student portal notes)

4. **`editor-styles.css`** — Shared CSS for ProseMirror editors
   - Image selection outline, resize handle styling, alignment CSS

### Image Upload Pipeline
- Images uploaded to **Azure Blob Storage** via backend content service
- Upload endpoint: `POST /api/content/media/upload` (through gateway at port 8080)
- Media API in shared-utils: `MediaApi.uploadMedia(file, folder)`
- Folders defined in `MediaFolderConstants.java`: `COURSE_IMAGES`, `LECTURE_IMAGES`, `CONTENT_IMAGES`

### Key Gotchas
- **`BubbleMenu` import path**: Must use `@tiptap/react/menus`, not `@tiptap/react`
- **NodeView alignment**: Must set `width: fit-content` on the resize wrapper div, otherwise `margin-left/right: auto` has no visual effect on a full-width block
- **NodeView update**: The parent `ResizableNodeView.update()` may not re-render for custom attribute changes (like alignment). Must intercept `update` and apply styles manually
- **tiptap-markdown serialization**: Custom markdown serializers MUST be defined via `addStorage()` → `markdown.serialize(state, node)`. The `getMarkdownSpec()` function in `tiptap-markdown/src/util/extensions.js` reads from `extension.storage?.markdown`. A top-level `renderMarkdown` property does NOT work.
- **tiptap-markdown HTML attribute parsing**: When tiptap-markdown parses `<img>` HTML tags from markdown content, standard attributes (like `width`) survive because they're defined in the Image extension schema. Custom attributes (like `data-alignment`) also parse correctly IF the extension's `parseHTML` function is defined. However, inline `style` attributes may be sanitized. As a safeguard, the `alignment` attribute's `parseHTML` also checks inline styles for margin-based alignment inference.
- **CSS scoping for read-only viewers**: Selected node styles (e.g., blue outline on images) must be scoped to `[contenteditable="true"]` to avoid showing in read-only viewers like the student portal's `TipTapContentViewer`
- **Content sections in lectures**: Use `TipTapMarkdownEditor` (NOT `RichTextEditor`) because content is stored as markdown
- **Programmatic file input**: Browsers block setting file inputs via JS for security. Cannot automate file upload testing
- **MarkdownRenderer**: Both admin and student portal `MarkdownRenderer` components use `ResizableImage` (not plain `Image`) so they correctly parse alignment/width from `<img>` HTML tags in markdown content

## Page Routes
- Course edit: `/courses/[id]`
- Lecture/sub-lecture edit: `/courses/[id]/lectures/[lectureId]/edit?subLectureId=[id]`
- Student portal course: `localhost:3001/courses/[id]`

## Build & Test Commands
```bash
# Rebuild shared-utils after changes
cd frontend/packages/shared-utils && npm run build

# Admin dashboard dev server (port 3000)
cd frontend/apps/admin-dashboard && npm run dev

# Student portal dev server (port 3001)
cd frontend/apps/student-portal && npm run dev

# Backend (Spring Boot, port 8080 gateway)
cd backend && ./gradlew bootRun
```
