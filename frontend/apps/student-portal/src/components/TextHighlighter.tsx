'use client'

import React, { useState, useEffect, useRef, useCallback } from 'react'
import type { Note } from '@kunal-ak23/edudron-shared-utils'

interface TextHighlighterProps {
  content: string
  notes: Note[]
  onHighlight: (selectedText: string, range: Range, color: string, noteText: string) => void
  className?: string
}

interface HighlightRange {
  start: number
  end: number
  color: string
  noteId: string
}

export function TextHighlighter({ content, notes, onHighlight, className = '' }: TextHighlighterProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [selection, setSelection] = useState<Range | null>(null)
  const [showColorPicker, setShowColorPicker] = useState(false)
  const [highlightRanges, setHighlightRanges] = useState<HighlightRange[]>([])

  // Convert notes to highlight ranges
  useEffect(() => {
    if (!containerRef.current || notes.length === 0) return

    const ranges: HighlightRange[] = []
    const textContent = containerRef.current.textContent || ''
    
    notes.forEach((note) => {
      if (note.highlightedText) {
        const start = textContent.indexOf(note.highlightedText)
        if (start !== -1) {
          ranges.push({
            start,
            end: start + note.highlightedText.length,
            color: note.highlightColor || '#FFEB3B',
            noteId: note.id
          })
        }
      }
    })

    setHighlightRanges(ranges)
  }, [notes])

  // Apply highlights to the content
  const applyHighlights = useCallback(() => {
    if (!containerRef.current) return

    const textContent = containerRef.current.textContent || ''
    let highlightedHTML = ''
    let lastIndex = 0

    // Sort ranges by start position
    const sortedRanges = [...highlightRanges].sort((a, b) => a.start - b.start)

    sortedRanges.forEach((range) => {
      // Add text before highlight
      if (range.start > lastIndex) {
        highlightedHTML += escapeHtml(textContent.substring(lastIndex, range.start))
      }

      // Add highlighted text
      const highlightedText = textContent.substring(range.start, range.end)
      highlightedHTML += `<mark style="background-color: ${range.color}; padding: 2px 0; cursor: pointer;" data-note-id="${range.noteId}">${escapeHtml(highlightedText)}</mark>`

      lastIndex = range.end
    })

    // Add remaining text
    if (lastIndex < textContent.length) {
      highlightedHTML += escapeHtml(textContent.substring(lastIndex))
    }

    // Only update if content changed to avoid infinite loops
    if (containerRef.current.innerHTML !== highlightedHTML) {
      containerRef.current.innerHTML = highlightedHTML
    }
  }, [highlightRanges])

  useEffect(() => {
    applyHighlights()
  }, [applyHighlights])

  const escapeHtml = (text: string) => {
    const div = document.createElement('div')
    div.textContent = text
    return div.innerHTML
  }

  const handleMouseUp = () => {
    const selection = window.getSelection()
    if (!selection || selection.rangeCount === 0) return

    const range = selection.getRangeAt(0)
    const selectedText = range.toString().trim()

    if (selectedText.length > 0 && containerRef.current?.contains(range.commonAncestorContainer)) {
      setSelection(range.cloneRange())
      setShowColorPicker(true)
    } else {
      setSelection(null)
      setShowColorPicker(false)
    }
  }

  const handleHighlight = (color: string, noteText: string) => {
    if (!selection) return

    const selectedText = selection.toString().trim()
    if (selectedText.length === 0) return

    // Get the text content and find the position
    const textContent = containerRef.current?.textContent || ''
    const start = textContent.indexOf(selectedText)
    
    if (start !== -1) {
      onHighlight(selectedText, selection, color, noteText)
    }

    // Clear selection
    window.getSelection()?.removeAllRanges()
    setSelection(null)
    setShowColorPicker(false)
  }

  return (
    <div className={`relative ${className}`}>
      <div
        ref={containerRef}
        onMouseUp={handleMouseUp}
        className="select-text"
        dangerouslySetInnerHTML={{ __html: content }}
      />
      
      {showColorPicker && selection && (
        <HighlightColorPicker
          selectedText={selection.toString().trim()}
          onSelectColor={handleHighlight}
          onClose={() => {
            setShowColorPicker(false)
            setSelection(null)
            window.getSelection()?.removeAllRanges()
          }}
        />
      )}
    </div>
  )
}

interface HighlightColorPickerProps {
  selectedText: string
  onSelectColor: (color: string, noteText: string) => void
  onClose: () => void
}

const HIGHLIGHT_COLORS = [
  { value: '#FFEB3B', name: 'Yellow' },
  { value: '#FFC107', name: 'Orange' },
  { value: '#4CAF50', name: 'Green' },
  { value: '#2196F3', name: 'Blue' },
  { value: '#9C27B0', name: 'Purple' },
  { value: '#F44336', name: 'Red' },
]

function HighlightColorPicker({ selectedText, onSelectColor, onClose }: HighlightColorPickerProps) {
  const [selectedColor, setSelectedColor] = useState(HIGHLIGHT_COLORS[0].value)
  const [noteText, setNoteText] = useState('')
  const [showNoteInput, setShowNoteInput] = useState(false)

  const handleColorSelect = (color: string) => {
    setSelectedColor(color)
    if (!showNoteInput) {
      setShowNoteInput(true)
    }
  }

  const handleSave = () => {
    onSelectColor(selectedColor, noteText)
  }

  return (
    <div className="fixed z-50 bg-white rounded-lg shadow-xl border border-gray-200 p-4 max-w-sm">
      <div className="flex items-center justify-between mb-3">
        <p className="text-sm font-medium text-gray-700">Highlight text</p>
        <button
          onClick={onClose}
          className="text-gray-400 hover:text-gray-600"
          aria-label="Close"
        >
          ✕
        </button>
      </div>

      <div className="mb-3 p-2 bg-gray-50 rounded text-xs text-gray-600 italic line-clamp-2">
        {selectedText}
      </div>

      <div className="mb-3">
        <p className="text-xs text-gray-600 mb-2">Choose color:</p>
        <div className="flex items-center space-x-2">
          {HIGHLIGHT_COLORS.map((color) => (
            <button
              key={color.value}
              onClick={() => handleColorSelect(color.value)}
              className={`w-8 h-8 rounded-full border-2 transition-all ${
                selectedColor === color.value
                  ? 'border-gray-800 scale-110'
                  : 'border-gray-300 hover:border-gray-400'
              }`}
              style={{ backgroundColor: color.value }}
              aria-label={color.name}
              title={color.name}
            />
          ))}
        </div>
      </div>

      {showNoteInput && (
        <div className="mb-3">
          <textarea
            value={noteText}
            onChange={(e) => setNoteText(e.target.value)}
            placeholder="Add a note (optional)..."
            rows={2}
            className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
            autoFocus
          />
        </div>
      )}

      <div className="flex items-center space-x-2">
        <button
          onClick={handleSave}
          className="flex-1 px-3 py-1.5 text-sm bg-primary-600 text-white rounded hover:bg-primary-700"
        >
          Save
        </button>
        <button
          onClick={onClose}
          className="px-3 py-1.5 text-sm border border-gray-300 rounded hover:bg-gray-50"
        >
          Cancel
        </button>
      </div>
    </div>
  )
}

