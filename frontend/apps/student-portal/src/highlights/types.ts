/**
 * Types and interfaces for the Highlights + Notes feature
 */

/**
 * TextPositionSelector: Stores start/end offsets in canonical text
 */
export interface TextPositionSelector {
  type: 'TextPositionSelector'
  start: number
  end: number
}

/**
 * TextQuoteSelector: Stores exact text with prefix/suffix context
 */
export interface TextQuoteSelector {
  type: 'TextQuoteSelector'
  exact: string
  prefix?: string
  suffix?: string
}

/**
 * DOMHint: Best-effort XPath/CSS path + node offsets for speed optimization
 */
export interface DomHint {
  xpath?: string
  cssPath?: string
  startNodeOffset?: number
  endNodeOffset?: number
}

/**
 * Highlight anchor: Combination of selectors for robust re-anchoring
 */
export interface HighlightAnchor {
  textPosition?: TextPositionSelector
  textQuote: TextQuoteSelector
  domHint?: DomHint
  contentHashAtCreate?: string // Hash of canonicalText at creation time
}

/**
 * Highlight record stored in backend
 */
export interface HighlightRecord {
  id: string
  documentId: string // e.g., lectureId or contentId
  userId: string // e.g., studentId
  anchor: HighlightAnchor
  color: string // Hex color code
  opacity?: number // Opacity/alpha value (0-1), defaults to 0.3
  noteText?: string
  createdAt: string
  updatedAt: string
  // Legacy fields for backward compatibility
  highlightedText?: string
  highlightColor?: string
  context?: string
}

/**
 * Resolved highlight with computed range
 */
export interface ResolvedHighlight {
  record: HighlightRecord
  range: Range | null
  isOrphaned: boolean
  confidence: 'high' | 'medium' | 'low'
}

/**
 * Text node mapping in canonical text index
 */
export interface TextNodeMapping {
  node: Text
  start: number
  end: number
}

/**
 * Canonical text index for a content root
 */
export interface TextIndex {
  canonicalText: string
  mappings: TextNodeMapping[]
  contentHash: string
}

/**
 * Highlight segment for rendering overlapping highlights
 */
export interface HighlightSegment {
  start: number
  end: number
  highlightIds: string[]
  colors: string[] // Multiple colors for overlapping highlights
}

/**
 * Options for building text index
 */
export interface TextIndexOptions {
  /**
   * Whether to normalize whitespace aggressively
   * @default false
   */
  normalizeWhitespace?: boolean
  /**
   * Whether to exclude certain elements (e.g., script, style, noscript)
   * @default true
   */
  excludeElements?: boolean
}

/**
 * Options for resolving highlights
 */
export interface ResolveOptions {
  /**
   * Whether to use domHint for faster resolution
   * @default true
   */
  useDomHint?: boolean
  /**
   * Minimum confidence level to accept
   * @default 'low'
   */
  minConfidence?: 'high' | 'medium' | 'low'
}

/**
 * Options for rendering highlights
 */
export interface RenderOptions {
  /**
   * CSS class prefix for highlight spans
   * @default 'hl'
   */
  classPrefix?: string
  /**
   * Whether to use gradient for overlapping highlights
   * @default false
   */
  useGradient?: boolean
  /**
   * Base opacity for overlapping highlights (0-1)
   * Lower values = lighter highlights, better text readability
   * @default 0.3
   */
  baseOpacity?: number
}

