/**
 * Anchor Resolution: Resolve highlight anchors to DOM ranges with fallback strategy
 */

import type {
  HighlightRecord,
  HighlightAnchor,
  ResolvedHighlight,
  TextIndex,
  ResolveOptions
} from './types'
import { offsetsToRange, extractTextQuote } from './textIndex'

/**
 * Simple hash function (must match the one in textIndex.ts)
 */
function simpleHash(str: string): string {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash
  }
  return Math.abs(hash).toString(36)
}

/**
 * Try to resolve using DOM hint (XPath/CSS path)
 * This is a best-effort optimization and may fail if DOM structure changed
 */
function tryDomHint(
  anchor: HighlightAnchor,
  root: Node
): Range | null {
  if (!anchor.domHint) {
    return null
  }

  try {
    // Try CSS path first (simpler and more reliable)
    if (anchor.domHint.cssPath) {
      const element = root.ownerDocument?.querySelector(anchor.domHint.cssPath)
      if (element) {
        const range = document.createRange()
        // This is a simplified version - in production, you'd want more robust path resolution
        // For now, we'll fall back to text quote matching
        return null
      }
    }

    // Try XPath (more complex, requires XPath evaluation)
    if (anchor.domHint.xpath && root.ownerDocument) {
      try {
        const result = root.ownerDocument.evaluate(
          anchor.domHint.xpath,
          root.ownerDocument,
          null,
          XPathResult.FIRST_ORDERED_NODE_TYPE,
          null
        )
        const node = result.singleNodeValue
        if (node) {
          // Similar to CSS path, would need more work for full implementation
          return null
        }
      } catch (e) {
        // XPath evaluation failed
      }
    }
  } catch (e) {
    // DOM hint resolution failed
  }

  return null
}

/**
 * Find all occurrences of a text quote in canonical text
 */
function findTextQuoteMatches(
  exact: string,
  prefix: string,
  suffix: string,
  canonicalText: string
): Array<{ start: number; end: number; score: number }> {
  const matches: Array<{ start: number; end: number; score: number }> = []
  const exactLower = exact.toLowerCase()
  const prefixLower = prefix.toLowerCase()
  const suffixLower = suffix.toLowerCase()

  let searchStart = 0
  while (true) {
    const exactIndex = canonicalText.toLowerCase().indexOf(exactLower, searchStart)
    if (exactIndex === -1) {
      break
    }

    const matchStart = exactIndex
    const matchEnd = exactIndex + exact.length

    // Score based on prefix/suffix match quality
    let score = 1.0

    // Check prefix match
    if (prefix.length > 0) {
      const actualPrefix = canonicalText.substring(
        Math.max(0, matchStart - prefix.length),
        matchStart
      ).toLowerCase()
      if (actualPrefix.endsWith(prefixLower)) {
        score += 0.5
      } else if (actualPrefix.length > 0) {
        // Partial prefix match
        const commonSuffix = findCommonSuffix(actualPrefix, prefixLower)
        score += (commonSuffix.length / prefix.length) * 0.3
      }
    }

    // Check suffix match
    if (suffix.length > 0) {
      const actualSuffix = canonicalText.substring(
        matchEnd,
        Math.min(canonicalText.length, matchEnd + suffix.length)
      ).toLowerCase()
      if (actualSuffix.startsWith(suffixLower)) {
        score += 0.5
      } else if (actualSuffix.length > 0) {
        // Partial suffix match
        const commonPrefix = findCommonPrefix(actualSuffix, suffixLower)
        score += (commonPrefix.length / suffix.length) * 0.3
      }
    }

    matches.push({ start: matchStart, end: matchEnd, score })
    searchStart = exactIndex + 1
  }

  // Sort by score (highest first)
  matches.sort((a, b) => b.score - a.score)

  return matches
}

/**
 * Helper: find common suffix of two strings
 */
function findCommonSuffix(a: string, b: string): string {
  let i = 0
  const minLen = Math.min(a.length, b.length)
  while (i < minLen && a[a.length - 1 - i] === b[b.length - 1 - i]) {
    i++
  }
  return a.substring(a.length - i)
}

/**
 * Helper: find common prefix of two strings
 */
function findCommonPrefix(a: string, b: string): string {
  let i = 0
  const minLen = Math.min(a.length, b.length)
  while (i < minLen && a[i] === b[i]) {
    i++
  }
  return a.substring(0, i)
}

/**
 * Resolve a highlight anchor to a DOM range
 * 
 * Uses a 4-step fallback strategy:
 * 1. If contentHash matches stored hash -> trust stored positions
 * 2. Try domHint (best-effort speed optimization)
 * 3. Try exact textQuote match with prefix/suffix scoring
 * 4. If not found -> mark as orphaned
 */
export function resolveHighlight(
  record: HighlightRecord,
  index: TextIndex,
  root: Node,
  options: ResolveOptions = {}
): ResolvedHighlight {
  const { anchor } = record
  const { useDomHint = true, minConfidence = 'low' } = options

  // Step 1: Check content hash match
  if (anchor.contentHashAtCreate && anchor.contentHashAtCreate === index.contentHash) {
    // Content hasn't changed, trust stored positions
    if (anchor.textPosition) {
      const range = offsetsToRange(
        anchor.textPosition.start,
        anchor.textPosition.end,
        index
      )
      if (range) {
        return {
          record,
          range,
          isOrphaned: false,
          confidence: 'high'
        }
      }
    }
  }

  // Step 2: Try DOM hint (if enabled)
  if (useDomHint && anchor.domHint) {
    const range = tryDomHint(anchor, root)
    if (range) {
      return {
        record,
        range,
        isOrphaned: false,
        confidence: 'medium'
      }
    }
  }

  // Step 3: Try text quote matching
  const { exact, prefix = '', suffix = '' } = anchor.textQuote
  if (exact.length > 0) {
    const matches = findTextQuoteMatches(exact, prefix, suffix, index.canonicalText)

    if (matches.length > 0) {
      // Use the best match (highest score)
      const bestMatch = matches[0]
      const range = offsetsToRange(bestMatch.start, bestMatch.end, index)

      if (range) {
        // Determine confidence based on score and match quality
        let confidence: 'high' | 'medium' | 'low' = 'low'
        if (bestMatch.score >= 2.0) {
          confidence = 'high'
        } else if (bestMatch.score >= 1.5) {
          confidence = 'medium'
        }

        // If multiple matches, prefer the one closest to stored position (if available)
        if (matches.length > 1 && anchor.textPosition) {
          const storedStart = anchor.textPosition.start
          let bestRange = range
          let bestDistance = Math.abs(bestMatch.start - storedStart)

          for (let i = 1; i < Math.min(matches.length, 5); i++) {
            const match = matches[i]
            const distance = Math.abs(match.start - storedStart)
            if (distance < bestDistance && match.score >= bestMatch.score * 0.8) {
              const candidateRange = offsetsToRange(match.start, match.end, index)
              if (candidateRange) {
                bestRange = candidateRange
                bestDistance = distance
              }
            }
          }

          return {
            record,
            range: bestRange,
            isOrphaned: false,
            confidence
          }
        }

        return {
          record,
          range,
          isOrphaned: false,
          confidence
        }
      }
    }
  }

  // Step 4: Not found -> orphaned
  return {
    record,
    range: null,
    isOrphaned: true,
    confidence: 'low'
  }
}

/**
 * Resolve multiple highlights
 */
export function resolveHighlights(
  records: HighlightRecord[],
  index: TextIndex,
  root: Node,
  options: ResolveOptions = {}
): ResolvedHighlight[] {
  return records.map(record => resolveHighlight(record, index, root, options))
}

