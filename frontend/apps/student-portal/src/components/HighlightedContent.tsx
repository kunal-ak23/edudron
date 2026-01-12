/**
 * HighlightedContent: Wrapper component that integrates highlights with content
 * 
 * This component wraps content (HTML or markdown) and provides full highlight functionality.
 * Use this instead of Reader for a more complete integration.
 */

'use client'

import React, { useRef, useEffect, useState, useCallback } from 'react'
import { MarkdownRenderer } from './MarkdownRenderer'
import { HighlightSidebar } from './HighlightSidebar'
import type { HighlightRecord, ResolvedHighlight, TextIndex } from '../highlights/types'
import { buildTextIndex } from '../highlights/textIndex'
import { resolveHighlights } from '../highlights/anchorResolve'
import { clearRenderedHighlights, renderAllHighlights } from '../highlights/segmentRender'
import { fetchHighlights, createHighlight, updateHighlight, deleteHighlight } from '../highlights/api'
import { HighlightPopover } from './HighlightPopover'
import { rangeToOffsets, extractTextQuote } from '../highlights/textIndex'

export interface HighlightedContentProps {
  /**
   * Content to display (markdown or HTML)
   */
  content: string
  /**
   * Whether content is markdown
   * @default true
   */
  isMarkdown?: boolean
  /**
   * Document ID (e.g., lectureId)
   */
  documentId: string
  /**
   * User ID (e.g., studentId)
   */
  userId: string
  /**
   * Callback when highlight is clicked
   */
  onHighlightClick?: (highlightId: string) => void
  /**
   * Custom className
   */
  className?: string
  /**
   * Whether to show sidebar
   */
  showSidebar?: boolean
  /**
   * Callback when sidebar toggle is requested
   */
  onSidebarToggle?: (isOpen: boolean) => void
}

// Lighter, more pastel colors for better text readability
const DEFAULT_HIGHLIGHT_COLORS = [
  { value: '#FFF9C4', name: 'Yellow' },      // Lighter yellow
  { value: '#FFE0B2', name: 'Orange' },      // Lighter orange
  { value: '#C8E6C9', name: 'Green' },        // Lighter green
  { value: '#BBDEFB', name: 'Blue' },         // Lighter blue
  { value: '#E1BEE7', name: 'Purple' },       // Lighter purple
  { value: '#FFCDD2', name: 'Red' },          // Lighter red
]

export function HighlightedContent({
  content,
  isMarkdown = true,
  documentId,
  userId,
  onHighlightClick,
  className = '',
  showSidebar = false,
  onSidebarToggle
}: HighlightedContentProps) {
  const contentRef = useRef<HTMLDivElement>(null)
  const [highlights, setHighlights] = useState<HighlightRecord[]>([])
  const [resolvedHighlights, setResolvedHighlights] = useState<ResolvedHighlight[]>([])
  const [textIndex, setTextIndex] = useState<TextIndex | null>(null)
  const [selection, setSelection] = useState<Range | null>(null)
  const [showPopover, setShowPopover] = useState(false)
  const [popoverPosition, setPopoverPosition] = useState({ top: 0, left: 0 })
  const [selectedText, setSelectedText] = useState('')
  const [selectedColor, setSelectedColor] = useState(DEFAULT_HIGHLIGHT_COLORS[0].value)
  const [selectedOpacity, setSelectedOpacity] = useState(0.3)
  const [noteText, setNoteText] = useState('')
  const [editingHighlightId, setEditingHighlightId] = useState<string | null>(null)
  const selectionRef = useRef<Range | null>(null)

  // Fetch highlights
  useEffect(() => {
    if (!documentId || !userId) return

    fetchHighlights(documentId, userId)
      .then(setHighlights)
      .catch(err => console.error('Failed to fetch highlights:', err))
  }, [documentId, userId])

  // Build text index when content is ready
  useEffect(() => {
    if (!contentRef.current) return

    const timeoutId = setTimeout(() => {
      if (!contentRef.current) return
      const index = buildTextIndex(contentRef.current!)
      setTextIndex(index)
    }, isMarkdown ? 300 : 0)

    return () => clearTimeout(timeoutId)
  }, [content, isMarkdown])

  // Resolve and render highlights
  useEffect(() => {
    if (!contentRef.current || !textIndex || highlights.length === 0) {
      if (contentRef.current && textIndex) {
        clearRenderedHighlights(contentRef.current)
      }
      setResolvedHighlights([])
      return
    }

    clearRenderedHighlights(contentRef.current)
    const resolved = resolveHighlights(highlights, textIndex, contentRef.current)
    setResolvedHighlights(resolved)
    renderAllHighlights(contentRef.current, resolved, textIndex)
  }, [textIndex, highlights])

  // Handle selection
  const handleSelectionChange = useCallback(() => {
    const selection = window.getSelection()
    if (!selection || selection.rangeCount === 0) return

    const range = selection.getRangeAt(0)
    if (!contentRef.current || !contentRef.current.contains(range.commonAncestorContainer)) {
      return
    }

    if (range.collapsed) return

    const parent = range.commonAncestorContainer.parentElement
    if (parent?.hasAttribute('data-hl')) {
      const highlightId = parent.getAttribute('data-hl-ids')?.split(',')[0]
      if (highlightId) {
        handleHighlightClick(highlightId)
      }
      return
    }

    const text = range.toString().trim()
    if (text.length === 0 || /^\s+$/.test(text)) return

    selectionRef.current = range.cloneRange()
    setSelection(range.cloneRange())
    setSelectedText(text)
    setNoteText('')
    setEditingHighlightId(null)

    const rect = range.getBoundingClientRect()
    const rootRect = contentRef.current.getBoundingClientRect()
    setPopoverPosition({
      top: rect.bottom - rootRect.top + 5,
      left: rect.left - rootRect.left
    })
    setShowPopover(true)
  }, [])

  useEffect(() => {
    document.addEventListener('selectionchange', handleSelectionChange)
    document.addEventListener('mouseup', handleSelectionChange)
    return () => {
      document.removeEventListener('selectionchange', handleSelectionChange)
      document.removeEventListener('mouseup', handleSelectionChange)
    }
  }, [handleSelectionChange])

  const handleHighlightClick = useCallback((highlightId: string) => {
    const highlight = highlights.find(h => h.id === highlightId)
    if (!highlight) return

    setEditingHighlightId(highlightId)
    setSelectedText(highlight.anchor.textQuote.exact)
    setSelectedColor(highlight.color)
    setSelectedOpacity((highlight as any).opacity || 0.3) // Use stored opacity or default
    setNoteText(highlight.noteText || '')

    if (contentRef.current) {
      const element = contentRef.current.querySelector(`[data-hl-ids*="${highlightId}"]`)
      if (element) {
        const rect = element.getBoundingClientRect()
        const rootRect = contentRef.current.getBoundingClientRect()
        setPopoverPosition({
          top: rect.bottom - rootRect.top + 5,
          left: rect.left - rootRect.left
        })
        setShowPopover(true)
        element.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    }

    if (onHighlightClick) {
      onHighlightClick(highlightId)
    }
  }, [highlights, onHighlightClick])

  const handleSaveHighlight = useCallback(async () => {
    if (!selectionRef.current || !textIndex || !contentRef.current) return

    try {
      const offsets = rangeToOffsets(selectionRef.current, textIndex)
      if (!offsets) return

      const textQuote = extractTextQuote(textIndex.canonicalText, offsets.start, offsets.end)
      const anchor = {
        textPosition: {
          type: 'TextPositionSelector' as const,
          start: offsets.start,
          end: offsets.end
        },
        textQuote: {
          type: 'TextQuoteSelector' as const,
          exact: textQuote.exact,
          prefix: textQuote.prefix,
          suffix: textQuote.suffix
        },
        contentHashAtCreate: textIndex.contentHash
      }

      if (editingHighlightId) {
        const updated = await updateHighlight(editingHighlightId, {
          color: selectedColor,
          opacity: selectedOpacity,
          noteText: noteText
        })
        setHighlights(prev => prev.map(h => h.id === editingHighlightId ? updated : h))
      } else {
        const newHighlight = await createHighlight({
          documentId,
          anchor,
          color: selectedColor,
          opacity: selectedOpacity,
          noteText: noteText
        })
        setHighlights(prev => [...prev, newHighlight])
      }

      window.getSelection()?.removeAllRanges()
      selectionRef.current = null
      setSelection(null)
      setShowPopover(false)
      setEditingHighlightId(null)
    } catch (error) {
      console.error('Failed to save highlight:', error)
    }
  }, [selectionRef.current, textIndex, selectedColor, noteText, editingHighlightId, documentId])

  const handleDeleteHighlight = useCallback(async (highlightId: string) => {
    try {
      await deleteHighlight(highlightId)
      setHighlights(prev => prev.filter(h => h.id !== highlightId))
      setShowPopover(false)
      setEditingHighlightId(null)
    } catch (error) {
      console.error('Failed to delete highlight:', error)
    }
  }, [])

  const handleCancel = useCallback(() => {
    window.getSelection()?.removeAllRanges()
    selectionRef.current = null
    setSelection(null)
    setShowPopover(false)
    setEditingHighlightId(null)
    setNoteText('')
  }, [])

  return (
    <div className={`relative ${className}`}>
      {/* Content wrapper */}
      <div
        ref={contentRef}
        className="highlight-content-root"
        data-highlight-exclude="false"
      >
        {isMarkdown ? (
          <MarkdownRenderer content={content} />
        ) : (
          <div dangerouslySetInnerHTML={{ __html: content }} />
        )}
      </div>

      {/* Popover */}
      <HighlightPopover
        isOpen={showPopover}
        position={popoverPosition}
        selectedText={selectedText}
        selectedColor={selectedColor}
        selectedOpacity={selectedOpacity}
        noteText={noteText}
        highlightColors={DEFAULT_HIGHLIGHT_COLORS}
        onColorChange={setSelectedColor}
        onOpacityChange={setSelectedOpacity}
        onNoteChange={setNoteText}
        onSave={handleSaveHighlight}
        onCancel={handleCancel}
        mode={editingHighlightId ? 'edit' : 'create'}
      />

      {/* Sidebar */}
      {showSidebar && (
        <HighlightSidebar
          highlights={resolvedHighlights}
          onHighlightClick={handleHighlightClick}
          onDeleteHighlight={handleDeleteHighlight}
          isOpen={showSidebar}
          onClose={() => onSidebarToggle?.(false)}
        />
      )}
    </div>
  )
}

