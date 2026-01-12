# Highlights + Notes Feature

A robust, production-ready highlighting and annotation system for LMS reading pages. Supports both HTML and markdown-rendered content, handles overlapping highlights correctly, and provides robust re-anchoring when content changes.

## Features

- ✅ **Text Selection & Highlighting**: Select any visible text and highlight it with optional colors
- ✅ **Notes**: Attach notes to highlights
- ✅ **Persistence**: Highlights are saved to backend and restored on page load
- ✅ **HTML & Markdown Support**: Works with both pre-rendered HTML and client-rendered markdown
- ✅ **Overlapping Highlights**: Correctly handles multiple overlapping highlights without breaking DOM
- ✅ **Robust Anchoring**: Uses multiple selector types (TextPosition, TextQuote, DOM hints) for reliable re-anchoring
- ✅ **Orphaned Highlight Detection**: Identifies highlights that can't be re-anchored due to content changes
- ✅ **Performance**: Efficient text indexing with caching

## Architecture

### Core Modules

1. **types.ts**: TypeScript interfaces and types for highlights, anchors, selectors
2. **textIndex.ts**: Builds canonical text index from DOM, converts between ranges and offsets
3. **anchorResolve.ts**: Resolves highlight anchors to DOM ranges with 4-step fallback strategy
4. **segmentRender.ts**: Segment-based rendering for overlapping highlights
5. **api.ts**: API client for fetching/creating/updating/deleting highlights

### Components

1. **HighlightedContent.tsx**: Main wrapper component (recommended for most use cases)
2. **Reader.tsx**: Lower-level component for custom integrations
3. **HighlightPopover.tsx**: UI for adding/editing highlights and notes
4. **HighlightSidebar.tsx**: Sidebar for listing highlights and orphaned highlights

## Usage

### Basic Usage (Recommended)

```tsx
import { HighlightedContent } from '@/components/HighlightedContent'

function MyPage() {
  const [showSidebar, setShowSidebar] = useState(false)

  return (
    <div>
      <button onClick={() => setShowSidebar(!showSidebar)}>
        Toggle Highlights
      </button>
      
      <HighlightedContent
        content={markdownContent}
        isMarkdown={true}
        documentId={lectureId}
        userId={studentId}
        showSidebar={showSidebar}
        onSidebarToggle={setShowSidebar}
      />
    </div>
  )
}
```

### Advanced Usage (Custom Integration)

```tsx
import { Reader } from '@/components/Reader'

function CustomReader() {
  const contentRef = useRef<HTMLDivElement>(null)

  return (
    <div>
      <div ref={contentRef}>
        <MarkdownRenderer content={content} />
      </div>
      
      {contentRef.current && (
        <Reader
          contentRoot={contentRef.current}
          documentId={lectureId}
          userId={studentId}
          isMarkdown={true}
        />
      )}
    </div>
  )
}
```

## How It Works

### 1. Text Indexing

When content is loaded, `buildTextIndex()` walks the DOM in reading order and creates:
- **canonicalText**: Normalized text string (NBSP→space, normalized line endings)
- **mappings**: Array mapping canonical text positions to actual DOM text nodes
- **contentHash**: Hash of canonical text for change detection

### 2. Capturing Selections

When user selects text:
1. Range is clamped to content root
2. Whitespace-only selections are rejected
3. Canonical offsets are computed from Range
4. TextQuote selector is created with prefix/suffix context
5. Popover UI appears for color selection and note entry

### 3. Storing Highlights

Each highlight is stored with:
- **TextPositionSelector**: `{ start, end }` offsets in canonical text
- **TextQuoteSelector**: `{ exact, prefix, suffix }` for text matching
- **domHint**: Optional XPath/CSS path for speed optimization
- **contentHashAtCreate**: Hash of content at creation time

### 4. Rendering Highlights

Highlights are rendered using a **segment-based approach**:
1. All highlight boundaries are collected
2. Non-overlapping segments are created
3. Each segment is wrapped with a span indicating which highlight(s) cover it
4. Overlapping highlights share segments (no nested spans)

### 5. Re-anchoring on Load

When highlights are loaded, they're resolved using a 4-step fallback:

1. **Content Hash Match**: If `contentHashAtCreate === currentContentHash`, trust stored positions
2. **DOM Hint**: Try XPath/CSS path (best-effort speed optimization)
3. **Text Quote Matching**: Find exact text with prefix/suffix scoring (handles repeated text)
4. **Orphaned**: If not found, mark as orphaned (shown in sidebar)

## Storage Model

### Backend Schema

The `Note` entity supports both legacy and new anchor-based storage:

```java
// Legacy fields (backward compatible)
@Column(columnDefinition = "text")
private String highlightedText;

@Column(columnDefinition = "text")
private String context;

// New anchor-based fields
@Column(columnDefinition = "text")
private String anchorJson; // JSON: HighlightAnchor

@Column(length = 100)
private String contentHashAtCreate;
```

### Frontend Types

```typescript
interface HighlightRecord {
  id: string
  documentId: string
  userId: string
  anchor: HighlightAnchor
  color: string
  noteText?: string
  createdAt: string
  updatedAt: string
}

interface HighlightAnchor {
  textPosition?: TextPositionSelector
  textQuote: TextQuoteSelector
  domHint?: DomHint
  contentHashAtCreate?: string
}
```

## Edge Cases Handled

- ✅ Selection starts/ends in non-text nodes
- ✅ Selection includes existing highlight spans
- ✅ Selection outside root container
- ✅ Repeated occurrences of same text across page
- ✅ Content changes (markdown edits) causing offsets to drift
- ✅ Performance on large docs (cached TextIndex, rebuild only when needed)
- ✅ Cross-block selections (paragraph to list item, etc.)
- ✅ Event handlers don't break selection on click

## Testing

Unit tests are provided for:
- Text indexing (`textIndex.test.ts`)
- Anchor resolution (`anchorResolve.test.ts`)

Run tests with:
```bash
npm test -- highlights
```

## API Integration

The `api.ts` module provides functions that can be extended to use your actual backend:

```typescript
// Fetch highlights for a document
fetchHighlights(documentId: string, userId: string): Promise<HighlightRecord[]>

// Create a new highlight
createHighlight(request: CreateHighlightRequest): Promise<HighlightRecord>

// Update an existing highlight
updateHighlight(highlightId: string, request: UpdateHighlightRequest): Promise<HighlightRecord>

// Delete a highlight
deleteHighlight(highlightId: string): Promise<void>
```

Currently, these functions attempt to use the existing `/api/lectures/{id}/notes` endpoint and convert between legacy and new formats. Update them to use your dedicated highlights API when available.

## Styling

Highlights use CSS classes with the prefix `hl` (configurable via `RenderOptions`):

- `.hl-segment`: Base class for highlight segments
- `[data-hl]`: Attribute containing highlight IDs
- `[data-hl-ids]`: Comma-separated list of highlight IDs
- `[data-hl-count]`: Number of overlapping highlights

Customize styles by targeting these classes or by passing custom `RenderOptions` to `renderAllHighlights()`.

## Performance Considerations

- Text index is built once per content load and cached
- Index is rebuilt only when content changes
- Selection events are debounced (100ms)
- Large documents: Index building is O(n) where n = text nodes
- Rendering: Segment building is O(m log m) where m = number of highlights

## Migration from Legacy System

The system is backward compatible with existing notes that use `highlightedText`. When loading:

1. Legacy notes are converted to `HighlightRecord` format
2. A basic `TextQuoteSelector` is created from `highlightedText`
3. Re-anchoring attempts to find the text in current content
4. If found, highlight is rendered; if not, marked as orphaned

To migrate existing notes:
1. Backfill `anchorJson` field with proper anchors
2. Set `contentHashAtCreate` for validation
3. Gradually deprecate `highlightedText` field

## License

Part of the Edudron LMS project.

