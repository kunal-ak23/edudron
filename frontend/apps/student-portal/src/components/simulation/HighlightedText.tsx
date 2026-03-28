'use client'

import { useMemo } from 'react'

interface HighlightedTextProps {
  text: string
  keywords: string[]
  className?: string
}

/**
 * Renders text with keyword terms highlighted as underlined spans.
 * Uses case-insensitive matching on word boundaries.
 */
export function HighlightedText({ text, keywords, className }: HighlightedTextProps) {
  const parts = useMemo(() => {
    if (!keywords.length || !text) return [{ text, highlight: false }]

    // Build a regex that matches any keyword (case-insensitive, word-boundary aware)
    const escaped = keywords
      .filter(k => k.length > 0)
      .map(k => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    if (!escaped.length) return [{ text, highlight: false }]

    const pattern = new RegExp(`(${escaped.join('|')})`, 'gi')
    const result: Array<{ text: string; highlight: boolean }> = []
    let lastIndex = 0

    text.replace(pattern, (match, _group, offset) => {
      if (offset > lastIndex) {
        result.push({ text: text.slice(lastIndex, offset), highlight: false })
      }
      result.push({ text: match, highlight: true })
      lastIndex = offset + match.length
      return match
    })

    if (lastIndex < text.length) {
      result.push({ text: text.slice(lastIndex), highlight: false })
    }

    return result.length ? result : [{ text, highlight: false }]
  }, [text, keywords])

  return (
    <span className={className}>
      {parts.map((part, i) =>
        part.highlight ? (
          <mark
            key={i}
            className="bg-[#6cd3f7]/15 text-[#6cd3f7] rounded px-0.5 decoration-[#6cd3f7]/40 underline underline-offset-2"
          >
            {part.text}
          </mark>
        ) : (
          <span key={i}>{part.text}</span>
        )
      )}
    </span>
  )
}

/**
 * Returns which keywords from the list appear in the given text.
 */
export function filterMatchingKeywords(
  text: string,
  keywords: Array<{ term: string; explanation: string }>
): Array<{ term: string; explanation: string }> {
  if (!text || !keywords?.length) return []
  const lower = text.toLowerCase()
  return keywords.filter(kw => lower.includes(kw.term.toLowerCase()))
}
