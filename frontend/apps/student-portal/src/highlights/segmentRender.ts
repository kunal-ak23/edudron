/**
 * Segment-based rendering for overlapping highlights
 * 
 * Instead of wrapping each highlight directly (which breaks with overlaps),
 * we build non-overlapping segments and wrap each segment with spans that
 * indicate which highlight(s) cover it.
 */

import type {
  HighlightRecord,
  ResolvedHighlight,
  HighlightSegment,
  TextIndex,
  RenderOptions
} from './types'
import { getTextNodesIntersectingRange } from './textIndex'

/**
 * Build non-overlapping segments from highlight boundaries
 */
export function buildSegments(
  resolvedHighlights: ResolvedHighlight[]
): HighlightSegment[] {
  // Collect all boundary points
  const boundaries = new Set<number>()

  resolvedHighlights.forEach(({ record, range }) => {
    if (range && !range.collapsed) {
      const text = range.toString()
      // We need to get offsets - for now, we'll use a simpler approach
      // In production, you'd want to get offsets from the range
      const startContainer = range.startContainer
      const endContainer = range.endContainer

      // For segment building, we'll work with ranges directly
      // This is a simplified version - full implementation would use offsets
    }
  })

  // For now, return segments based on resolved highlights
  // Full implementation would merge overlapping ranges
  const segments: HighlightSegment[] = []

  resolvedHighlights.forEach(({ record, range }) => {
    if (range && !range.collapsed) {
      segments.push({
        start: 0, // Placeholder - would be computed from range offsets
        end: 0, // Placeholder
        highlightIds: [record.id],
        colors: [record.color]
      })
    }
  })

  return segments
}

/**
 * Split a text node and wrap a portion with a span
 */
export function splitAndWrapTextNodePortion(
  node: Text,
  startOffset: number,
  endOffset: number,
  wrapperMeta: { class: string; attributes: Record<string, string> }
): void {
  const text = node.textContent || ''
  const parent = node.parentNode
  if (!parent) return

  // Split the text node into three parts: before, selected, after
  const beforeText = text.substring(0, startOffset)
  const selectedText = text.substring(startOffset, endOffset)
  const afterText = text.substring(endOffset)

  // Create wrapper span
  const span = document.createElement('span')
  span.className = wrapperMeta.class
  Object.entries(wrapperMeta.attributes).forEach(([key, value]) => {
    span.setAttribute(key, value)
  })
  span.textContent = selectedText

  // Replace the original node with: beforeText node + span + afterText node
  const fragment = document.createDocumentFragment()

  if (beforeText.length > 0) {
    fragment.appendChild(document.createTextNode(beforeText))
  }

  fragment.appendChild(span)

  if (afterText.length > 0) {
    fragment.appendChild(document.createTextNode(afterText))
  }

  parent.replaceChild(fragment, node)
}

/**
 * Clear all rendered highlights from a root
 * Removes only highlight wrapper spans, preserving text content
 */
export function clearRenderedHighlights(root: Node): void {
  const highlights = root.querySelectorAll('[data-hl]')
  // Convert to array and process in reverse to avoid issues
  const highlightsArray = Array.from(highlights).reverse()
  
  highlightsArray.forEach(highlight => {
    const parent = highlight.parentNode
    if (parent && parent.contains(highlight)) {
      try {
        // Replace highlight span with its text content
        const textNode = document.createTextNode(highlight.textContent || '')
        parent.replaceChild(textNode, highlight)
        // Normalize to merge adjacent text nodes
        parent.normalize()
      } catch (e) {
        // Node might have already been removed
        console.warn('Failed to clear highlight, node may have been removed:', e)
      }
    }
  })
}

/**
 * Render highlights using segment-based approach
 * 
 * This function handles overlapping highlights by:
 * 1. Finding all text nodes intersecting with highlight ranges
 * 2. Splitting text nodes at boundaries
 * 3. Wrapping segments with spans that indicate which highlights cover them
 */
export function renderHighlights(
  root: Node,
  resolvedHighlights: ResolvedHighlight[],
  index: TextIndex,
  options: RenderOptions = {}
): void {
  const {
    classPrefix = 'hl',
    useGradient = false,
    baseOpacity = 0.3  // Reduced from 0.7 to make highlights lighter and more readable
  } = options

  // Clear existing highlights first
  clearRenderedHighlights(root)

  // Filter out orphaned highlights
  const validHighlights = resolvedHighlights.filter(h => !h.isOrphaned && h.range)

  if (validHighlights.length === 0) {
    return
  }

  // Collect all text nodes that need to be modified
  const textNodeRanges = new Map<Text, Array<{ start: number; end: number; highlightIds: string[]; colors: string[]; opacities: number[] }>>()

  validHighlights.forEach(({ record, range }) => {
    if (!range) return

    const textNodes = getTextNodesIntersectingRange(range)

    textNodes.forEach(textNode => {
      // Skip if text node is already inside a highlight span
      let parent = textNode.parentElement
      while (parent && parent !== root) {
        if (parent.hasAttribute('data-hl')) {
          return // Skip this node, it's already inside a highlight
        }
        parent = parent.parentElement
      }

      // Check if node is still in the DOM
      if (!textNode.parentNode) {
        return
      }

      // Calculate the intersection of the range with this text node
      const nodeRange = document.createRange()
      try {
        nodeRange.selectNodeContents(textNode)
      } catch (e) {
        // Node might be invalid, skip
        return
      }

      const intersection = range.intersection(nodeRange)
      if (!intersection || intersection.collapsed) return

      // Get offsets relative to the text node
      const nodeStart = intersection.startOffset
      const nodeEnd = intersection.endOffset

      if (!textNodeRanges.has(textNode)) {
        textNodeRanges.set(textNode, [])
      }

      const ranges = textNodeRanges.get(textNode)!
      ranges.push({
        start: nodeStart,
        end: nodeEnd,
        highlightIds: [record.id],
        colors: [record.color],
        opacities: [record.opacity ?? baseOpacity] // Use highlight's opacity or fallback to baseOpacity
      })
    })
  })

  // Helper to convert hex to rgba with opacity
  const hexToRgba = (hex: string, opacity: number): string => {
    const r = parseInt(hex.slice(1, 3), 16)
    const g = parseInt(hex.slice(3, 5), 16)
    const b = parseInt(hex.slice(5, 7), 16)
    return `rgba(${r}, ${g}, ${b}, ${opacity})`
  }

  // Helper to blend multiple colors (average RGB values)
  const blendColors = (colors: string[]): string => {
    let totalR = 0
    let totalG = 0
    let totalB = 0

    colors.forEach(color => {
      const r = parseInt(color.slice(1, 3), 16)
      const g = parseInt(color.slice(3, 5), 16)
      const b = parseInt(color.slice(5, 7), 16)
      totalR += r
      totalG += g
      totalB += b
    })

    const count = colors.length
    const avgR = Math.round(totalR / count)
    const avgG = Math.round(totalG / count)
    const avgB = Math.round(totalB / count)

    return `#${avgR.toString(16).padStart(2, '0')}${avgG.toString(16).padStart(2, '0')}${avgB.toString(16).padStart(2, '0')}`
  }

  // Convert Map to Array and process in reverse order to avoid offset issues
  // Process from last to first to prevent earlier replacements from affecting later ones
  const textNodeEntries = Array.from(textNodeRanges.entries()).reverse()

  textNodeEntries.forEach(([textNode, ranges]) => {
    if (ranges.length === 0) return

    // Check if text node is still valid and in the DOM
    if (!textNode.parentNode || !textNode.parentNode.contains(textNode)) {
      return // Node has already been replaced or removed
    }

    // Sort ranges by start position
    ranges.sort((a, b) => a.start - b.start)

    // Collect all unique boundary points
    const boundaries = new Set<number>()
    ranges.forEach(range => {
      boundaries.add(range.start)
      boundaries.add(range.end)
    })
    const sortedBoundaries = Array.from(boundaries).sort((a, b) => a - b)

    // Create segments from boundaries
    const segments: Array<{ start: number; end: number; highlightIds: string[]; colors: string[]; opacities: number[] }> = []
    
    for (let i = 0; i < sortedBoundaries.length - 1; i++) {
      const segmentStart = sortedBoundaries[i]
      const segmentEnd = sortedBoundaries[i + 1]
      
      // Find all highlights that cover this segment
      const coveringHighlights: Array<{ id: string; color: string; opacity: number }> = []
      ranges.forEach(range => {
        if (range.start <= segmentStart && range.end >= segmentEnd) {
          coveringHighlights.push({
            id: range.highlightIds[0],
            color: range.colors[0],
            opacity: range.opacities[0]
          })
        }
      })

      if (coveringHighlights.length > 0) {
        segments.push({
          start: segmentStart,
          end: segmentEnd,
          highlightIds: coveringHighlights.map(h => h.id),
          colors: coveringHighlights.map(h => h.color),
          opacities: coveringHighlights.map(h => h.opacity)
        })
      }
    }

    if (segments.length === 0) return

    // Now split and wrap the text node
    // Build the replacement fragment from start to end
    const text = textNode.textContent || ''
    const fragment = document.createDocumentFragment()
    let lastIndex = 0

    segments.forEach(segment => {
      // Add text before this segment
      if (segment.start > lastIndex) {
        const beforeText = text.substring(lastIndex, segment.start)
        if (beforeText.length > 0) {
          fragment.appendChild(document.createTextNode(beforeText))
        }
      }

      // Create wrapper span for this segment
      const span = document.createElement('span')
      span.className = `${classPrefix}-segment`
      span.setAttribute('data-hl', segment.highlightIds.join(','))
      span.setAttribute('data-hl-ids', segment.highlightIds.join(','))
      span.setAttribute('data-hl-count', segment.highlightIds.length.toString())

      // Set style based on number of overlapping highlights
      // For overlapping highlights, use average opacity
      const avgOpacity = segment.opacities.reduce((a, b) => a + b, 0) / segment.opacities.length
      
      if (segment.colors.length === 1) {
        span.style.backgroundColor = hexToRgba(segment.colors[0], segment.opacities[0])
      } else if (useGradient) {
        // Create gradient for overlapping highlights
        const gradientStops = segment.colors.map((color, idx) => {
          const stop = (idx / segment.colors.length) * 100
          const rgba = hexToRgba(color, segment.opacities[idx] || avgOpacity)
          return `${rgba} ${stop}%`
        }).join(', ')
        span.style.background = `linear-gradient(to right, ${gradientStops})`
      } else {
        // Blend colors for overlapping highlights (average the colors)
        const avgColor = blendColors(segment.colors)
        span.style.backgroundColor = hexToRgba(avgColor, avgOpacity)
      }

      span.style.padding = '2px 0'
      span.style.cursor = 'pointer'
      span.style.borderRadius = '2px'

      // Add the highlighted text
      const segmentText = text.substring(segment.start, segment.end)
      span.textContent = segmentText
      fragment.appendChild(span)

      lastIndex = segment.end
    })

    // Add remaining text after last segment
    if (lastIndex < text.length) {
      const afterText = text.substring(lastIndex)
      if (afterText.length > 0) {
        fragment.appendChild(document.createTextNode(afterText))
      }
    }

    // Replace the original text node with the fragment
    // Check if the node is still valid and attached to the DOM
    const parent = textNode.parentNode
    if (parent && parent.contains(textNode)) {
      try {
        parent.replaceChild(fragment, textNode)
      } catch (e) {
        // If replaceChild fails, the node might have already been replaced
        // This can happen if multiple highlights affect the same area
        console.warn('Failed to replace text node, it may have already been modified:', e)
      }
    }
  })
}

/**
 * Render all highlights (convenience function)
 */
export function renderAllHighlights(
  root: Node,
  resolvedHighlights: ResolvedHighlight[],
  index: TextIndex,
  options: RenderOptions = {}
): void {
  renderHighlights(root, resolvedHighlights, index, options)
}

