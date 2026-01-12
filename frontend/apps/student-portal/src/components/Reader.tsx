/**
 * Reader: Main component for reading content with highlights
 * 
 * Handles:
 * - Text selection and highlight creation
 * - Rendering highlights on content (HTML or markdown-rendered)
 * - Re-anchoring highlights on load
 * - Event handling for selection
 */

'use client'

import React, { useState, useEffect, useRef, useCallback } from 'react'
import type { HighlightRecord, TextIndex, ResolvedHighlight } from '../highlights/types'
import { buildTextIndex, rangeToOffsets, extractTextQuote } from '../highlights/textIndex'
import { resolveHighlights } from '../highlights/anchorResolve'
import { clearRenderedHighlights, renderAllHighlights } from '../highlights/segmentRender'
import { fetchHighlights, createHighlight, updateHighlight, deleteHighlight } from '../highlights/api'
import { HighlightPopover } from './HighlightPopover'

export interface ReaderProps {
  /**
   * Content root element ref or selector
   * If string, will querySelector; if HTMLElement, will use directly
   */
  contentRoot: HTMLElement | string
  /**
   * Document ID (e.g., lectureId, contentId)
   */
  documentId: string
  /**
   * User ID (e.g., studentId)
   */
  userId: string
  /**
   * Whether content is markdown (will be rendered to HTML)
   * @default false
   */
  isMarkdown?: boolean
  /**
   * Callback when content is ready (for markdown rendering)
   */
  onContentReady?: () => void
  /**
   * Highlight colors
   */
  highlightColors?: Array<{ value: string; name: string }>
  /**
   * Custom className
   */
  className?: string
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

export function Reader({
  contentRoot,
  documentId,
  userId,
  isMarkdown = false,
  onContentReady,
  highlightColors = DEFAULT_HIGHLIGHT_COLORS,
  className = ''
}: ReaderProps) {
  const [highlights, setHighlights] = useState<HighlightRecord[]>([])
  const [resolvedHighlights, setResolvedHighlights] = useState<ResolvedHighlight[]>([])
  const [textIndex, setTextIndex] = useState<TextIndex | null>(null)
  const [selection, setSelection] = useState<Range | null>(null)
  const [showPopover, setShowPopover] = useState(false)
  const [popoverPosition, setPopoverPosition] = useState({ top: 0, left: 0 })
  const [selectedText, setSelectedText] = useState('')
  const [selectedColor, setSelectedColor] = useState(highlightColors[0].value)
  const [selectedOpacity, setSelectedOpacity] = useState(0.3)
  const [noteText, setNoteText] = useState('')
  const [editingHighlightId, setEditingHighlightId] = useState<string | null>(null)

  const rootRef = useRef<HTMLElement | null>(null)
  const selectionRef = useRef<Range | null>(null)
  const debounceTimerRef = useRef<NodeJS.Timeout | null>(null)

  // Get root element
  useEffect(() => {
    if (typeof contentRoot === 'string') {
      rootRef.current = document.querySelector(contentRoot)
    } else {
      rootRef.current = contentRoot
    }
  }, [contentRoot])

  // Build text index when content changes
  useEffect(() => {
    if (!rootRef.current) return

    // Wait for content to be ready (especially for markdown)
    const timeoutId = setTimeout(() => {
      if (!rootRef.current) return

      const index = buildTextIndex(rootRef.current!)
      setTextIndex(index)

      if (onContentReady) {
        onContentReady()
      }
    }, isMarkdown ? 300 : 0)

    return () => clearTimeout(timeoutId)
  }, [rootRef.current, isMarkdown, onContentReady])

  // Fetch highlights on mount
  useEffect(() => {
    if (!documentId || !userId) return

    fetchHighlights(documentId, userId)
      .then(setHighlights)
      .catch(err => console.error('Failed to fetch highlights:', err))
  }, [documentId, userId])

  // Resolve and render highlights when index or highlights change
  useEffect(() => {
    if (!rootRef.current || !textIndex || highlights.length === 0) {
      // Clear highlights if no index or no highlights
      if (rootRef.current && textIndex) {
        clearRenderedHighlights(rootRef.current)
      }
      setResolvedHighlights([])
      return
    }

    // Clear existing highlights
    clearRenderedHighlights(rootRef.current)

    // Resolve highlights
    const resolved = resolveHighlights(highlights, textIndex, rootRef.current)
    setResolvedHighlights(resolved)

    // Render highlights
    renderAllHighlights(rootRef.current, resolved, textIndex)
  }, [textIndex, highlights])

  // Handle selection
  const handleSelectionChange = useCallback(() => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current)
    }

    debounceTimerRef.current = setTimeout(() => {
      const selection = window.getSelection()
      if (!selection || selection.rangeCount === 0) {
        return
      }

      const range = selection.getRangeAt(0)

      // Check if selection is inside root
      if (!rootRef.current || !rootRef.current.contains(range.commonAncestorContainer)) {
        return
      }

      // Check if selection is collapsed (no text selected)
      if (range.collapsed) {
        return
      }

      // Check if clicking on existing highlight
      const parent = range.commonAncestorContainer.parentElement
      if (parent?.hasAttribute('data-hl')) {
        // Handle highlight click
        const highlightId = parent.getAttribute('data-hl-ids')?.split(',')[0]
        if (highlightId) {
          handleHighlightClick(highlightId)
        }
        return
      }

      // Extract selected text
      const text = range.toString().trim()

      // Reject whitespace-only selections
      if (text.length === 0 || /^\s+$/.test(text)) {
        return
      }

      // Store selection
      selectionRef.current = range.cloneRange()
      setSelection(range.cloneRange())
      setSelectedText(text)
      setNoteText('')
      setEditingHighlightId(null)

      // Calculate popover position
      const rect = range.getBoundingClientRect()
      const rootRect = rootRef.current.getBoundingClientRect()

      setPopoverPosition({
        top: rect.bottom - rootRect.top + 5,
        left: rect.left - rootRect.left
      })

      setShowPopover(true)
    }, 100) // Debounce selection changes
  }, [])

  // Set up selection listeners
  useEffect(() => {
    document.addEventListener('selectionchange', handleSelectionChange)
    document.addEventListener('mouseup', handleSelectionChange)

    return () => {
      document.removeEventListener('selectionchange', handleSelectionChange)
      document.removeEventListener('mouseup', handleSelectionChange)
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current)
      }
    }
  }, [handleSelectionChange])

  // Handle highlight click
  const handleHighlightClick = useCallback((highlightId: string) => {
    const highlight = highlights.find(h => h.id === highlightId)
    if (!highlight) return

    setEditingHighlightId(highlightId)
    setSelectedText(highlight.anchor.textQuote.exact)
    setSelectedColor(highlight.color)
    setSelectedOpacity(highlight.opacity || 0.3)
    setNoteText(highlight.noteText || '')

    // Find highlight element and position popover
    if (rootRef.current) {
      const element = rootRef.current.querySelector(`[data-hl-ids*="${highlightId}"]`)
      if (element) {
        const rect = element.getBoundingClientRect()
        const rootRect = rootRef.current.getBoundingClientRect()

        setPopoverPosition({
          top: rect.bottom - rootRect.top + 5,
          left: rect.left - rootRect.left
        })

        setShowPopover(true)
      }
    }
  }, [highlights])

  // Handle save highlight
  const handleSaveHighlight = useCallback(async () => {
    if (!selectionRef.current || !textIndex || !rootRef.current) {
      return
    }

    try {
      // Get offsets
      const offsets = rangeToOffsets(selectionRef.current, textIndex)
      if (!offsets) {
        console.warn('Failed to get offsets from range')
        return
      }

      // Extract text quote
      const textQuote = extractTextQuote(
        textIndex.canonicalText,
        offsets.start,
        offsets.end
      )

      // Create anchor
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
        // Update existing highlight
        const updated = await updateHighlight(editingHighlightId, {
          color: selectedColor,
          opacity: selectedOpacity,
          noteText: noteText
        })

        setHighlights(prev => prev.map(h => h.id === editingHighlightId ? updated : h))
      } else {
        // Create new highlight
        const newHighlight = await createHighlight({
          documentId,
          anchor,
          color: selectedColor,
          opacity: selectedOpacity,
          noteText: noteText
        })

        setHighlights(prev => [...prev, newHighlight])
      }

      // Clear selection
      window.getSelection()?.removeAllRanges()
      selectionRef.current = null
      setSelection(null)
      setShowPopover(false)
      setEditingHighlightId(null)
    } catch (error) {
      console.error('Failed to save highlight:', error)
    }
  }, [selectionRef.current, textIndex, selectedColor, noteText, editingHighlightId, documentId])

  // Handle delete highlight
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

  // Handle cancel
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
      {/* Content is rendered by parent - we just handle highlights */}
      
      {/* Popover */}
      <HighlightPopover
        isOpen={showPopover}
        position={popoverPosition}
        selectedText={selectedText}
        selectedColor={selectedColor}
        selectedOpacity={selectedOpacity}
        noteText={noteText}
        highlightColors={highlightColors}
        onColorChange={setSelectedColor}
        onOpacityChange={setSelectedOpacity}
        onNoteChange={setNoteText}
        onSave={handleSaveHighlight}
        onCancel={handleCancel}
        mode={editingHighlightId ? 'edit' : 'create'}
      />
    </div>
  )
}

