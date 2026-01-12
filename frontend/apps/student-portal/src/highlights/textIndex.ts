/**
 * Text Indexing: Build canonical text index from DOM and convert between ranges and offsets
 */

import type { TextIndex, TextNodeMapping, TextIndexOptions } from './types'

/**
 * Simple hash function for content fingerprinting
 */
function simpleHash(str: string): string {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // Convert to 32-bit integer
  }
  return Math.abs(hash).toString(36)
}

/**
 * Normalize text: convert NBSP to space, normalize line endings
 */
function normalizeText(text: string, options: TextIndexOptions = {}): string {
  let normalized = text
    .replace(/\u00A0/g, ' ') // NBSP to space
    .replace(/\r\n/g, '\n') // CRLF to LF
    .replace(/\r/g, '\n') // CR to LF

  if (options.normalizeWhitespace) {
    // Aggressive whitespace normalization (only if explicitly requested)
    normalized = normalized.replace(/\s+/g, ' ')
  }

  return normalized
}

/**
 * Check if a node should be excluded from text indexing
 */
function shouldExcludeNode(node: Node): boolean {
  if (node.nodeType !== Node.ELEMENT_NODE) {
    return false
  }

  const element = node as Element
  const tagName = element.tagName.toLowerCase()

  // Exclude script, style, noscript, etc.
  const excludeTags = ['script', 'style', 'noscript', 'meta', 'link', 'head']
  if (excludeTags.includes(tagName)) {
    return true
  }

  // Exclude elements with data-highlight-exclude attribute
  if (element.hasAttribute('data-highlight-exclude')) {
    return true
  }

  return false
}

/**
 * Build a canonical text index from a DOM root
 * 
 * Walks the DOM in reading order, collecting TEXT_NODE contents and creating
 * a mapping between canonical text positions and actual DOM text nodes.
 */
export function buildTextIndex(
  root: Node,
  options: TextIndexOptions = {}
): TextIndex {
  const mappings: TextNodeMapping[] = []
  let canonicalText = ''

  const walker = document.createTreeWalker(
    root,
    NodeFilter.SHOW_TEXT,
    {
      acceptNode: (node) => {
        // Skip if inside excluded element
        if (options.excludeElements !== false) {
          let parent = node.parentElement
          while (parent && parent !== root) {
            if (shouldExcludeNode(parent)) {
              return NodeFilter.FILTER_REJECT
            }
            parent = parent.parentElement
          }
        }

        // Skip if inside existing highlight wrapper (to avoid double-counting)
        let parent = node.parentElement
        while (parent && parent !== root) {
          if (parent.hasAttribute('data-hl')) {
            return NodeFilter.FILTER_REJECT
          }
          parent = parent.parentElement
        }

        return NodeFilter.FILTER_ACCEPT
      }
    }
  )

  let node: Node | null
  while ((node = walker.nextNode())) {
    if (node.nodeType === Node.TEXT_NODE) {
      const textNode = node as Text
      const rawText = textNode.textContent || ''
      const normalized = normalizeText(rawText, options)

      if (normalized.length > 0) {
        const start = canonicalText.length
        const end = start + normalized.length

        mappings.push({
          node: textNode,
          start,
          end
        })

        canonicalText += normalized
      }
    }
  }

  const contentHash = simpleHash(canonicalText)

  return {
    canonicalText,
    mappings,
    contentHash
  }
}

/**
 * Convert a Range to canonical text offsets
 * 
 * Returns { start, end } offsets in the canonical text, or null if the range
 * cannot be mapped (e.g., if it's outside the root or in excluded content).
 */
export function rangeToOffsets(
  range: Range,
  index: TextIndex
): { start: number; end: number } | null {
  const { startContainer, startOffset, endContainer, endOffset } = range

  // Find start position
  let startPos: number | null = null
  let endPos: number | null = null

  // Helper to find offset in a text node
  const findTextNodeOffset = (
    container: Node,
    offset: number,
    mappings: TextNodeMapping[]
  ): number | null => {
    if (container.nodeType !== Node.TEXT_NODE) {
      // If container is not a text node, find the nearest text node
      // This handles cases where selection starts/ends in element nodes
      if (container.nodeType === Node.ELEMENT_NODE) {
        const element = container as Element
        if (offset === 0) {
          // Start of element - find first text node
          const walker = document.createTreeWalker(
            element,
            NodeFilter.SHOW_TEXT
          )
          const firstText = walker.nextNode()
          if (firstText) {
            return findTextNodeOffset(firstText, 0, mappings)
          }
        } else {
          // End of element - find last text node
          const walker = document.createTreeWalker(
            element,
            NodeFilter.SHOW_TEXT
          )
          let lastText: Node | null = null
          let node: Node | null
          while ((node = walker.nextNode())) {
            lastText = node
          }
          if (lastText) {
            const textNode = lastText as Text
            return findTextNodeOffset(textNode, textNode.textContent?.length || 0, mappings)
          }
        }
      }
      return null
    }

    const textNode = container as Text
    const rawText = textNode.textContent || ''
    const normalized = normalizeText(rawText)

    // Find the mapping for this text node
    const mapping = mappings.find(m => m.node === textNode)
    if (!mapping) {
      return null
    }

    // Map the offset in the raw text to canonical text
    // We need to account for normalization differences
    let canonicalOffset = 0
    let rawOffset = 0

    while (rawOffset < offset && rawOffset < rawText.length) {
      const char = rawText[rawOffset]
      const normalizedChar = normalizeText(char)

      if (normalizedChar.length > 0) {
        canonicalOffset++
      }

      rawOffset++
    }

    return mapping.start + canonicalOffset
  }

  startPos = findTextNodeOffset(startContainer, startOffset, index.mappings)
  endPos = findTextNodeOffset(endContainer, endOffset, index.mappings)

  if (startPos === null || endPos === null) {
    return null
  }

  // Ensure start <= end
  if (startPos > endPos) {
    [startPos, endPos] = [endPos, startPos]
  }

  return { start: startPos, end: endPos }
}

/**
 * Convert canonical text offsets to a Range
 * 
 * Creates a Range object spanning the text at the given offsets in the canonical text.
 */
export function offsetsToRange(
  start: number,
  end: number,
  index: TextIndex
): Range | null {
  if (start < 0 || end < 0 || start > end || end > index.canonicalText.length) {
    return null
  }

  const range = document.createRange()

  // Find start node and offset
  let startNode: Text | null = null
  let startOffset = 0
  let accumulated = 0

  for (const mapping of index.mappings) {
    if (start >= mapping.start && start < mapping.end) {
      startNode = mapping.node
      // Map canonical offset back to raw text offset
      const relativeOffset = start - mapping.start
      const rawText = mapping.node.textContent || ''
      const normalized = normalizeText(rawText)

      // Simple approximation: assume 1:1 mapping if lengths match
      // For more accuracy, we'd need to track normalization differences
      if (normalized.length === rawText.length) {
        startOffset = relativeOffset
      } else {
        // Fallback: use relative position
        startOffset = Math.floor((relativeOffset / normalized.length) * rawText.length)
      }
      break
    }
    accumulated = mapping.end
  }

  // Find end node and offset
  let endNode: Text | null = null
  let endOffset = 0

  for (const mapping of index.mappings) {
    if (end > mapping.start && end <= mapping.end) {
      endNode = mapping.node
      const relativeOffset = end - mapping.start
      const rawText = mapping.node.textContent || ''
      const normalized = normalizeText(rawText)

      if (normalized.length === rawText.length) {
        endOffset = relativeOffset
      } else {
        endOffset = Math.floor((relativeOffset / normalized.length) * rawText.length)
      }
      break
    }
  }

  if (!startNode || !endNode) {
    return null
  }

  try {
    range.setStart(startNode, startOffset)
    range.setEnd(endNode, endOffset)
    return range
  } catch (e) {
    console.warn('Failed to create range from offsets:', e)
    return null
  }
}

/**
 * Get all text nodes intersecting a range
 */
export function getTextNodesIntersectingRange(range: Range): Text[] {
  const textNodes: Text[] = []
  const walker = document.createTreeWalker(
    range.commonAncestorContainer,
    NodeFilter.SHOW_TEXT
  )

  let node: Node | null
  while ((node = walker.nextNode())) {
    if (node.nodeType === Node.TEXT_NODE) {
      const textNode = node as Text
      const nodeRange = document.createRange()
      try {
        nodeRange.selectNodeContents(textNode)
        if (range.intersectsNode(textNode) || range.compareBoundaryPoints(Range.START_TO_START, nodeRange) <= 0 && range.compareBoundaryPoints(Range.END_TO_END, nodeRange) >= 0) {
          textNodes.push(textNode)
        }
      } catch (e) {
        // Node might be in a different document, skip
      }
    }
  }

  return textNodes
}

/**
 * Extract text quote with prefix/suffix context
 */
export function extractTextQuote(
  canonicalText: string,
  start: number,
  end: number,
  contextLength: number = 32
): { exact: string; prefix: string; suffix: string } {
  const exact = canonicalText.substring(start, end)
  const prefixStart = Math.max(0, start - contextLength)
  const prefix = canonicalText.substring(prefixStart, start)
  const suffixEnd = Math.min(canonicalText.length, end + contextLength)
  const suffix = canonicalText.substring(end, suffixEnd)

  return { exact, prefix, suffix }
}

