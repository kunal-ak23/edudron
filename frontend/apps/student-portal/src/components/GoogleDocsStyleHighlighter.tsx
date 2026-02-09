'use client'

import React, { useState, useEffect, useRef, useCallback } from 'react'
import type { Note } from '@kunal-ak23/edudron-shared-utils'

interface Highlight {
  id: string
  start: number
  end: number
  text: string
  color: string
  noteText?: string
  noteId?: string
}

interface GoogleDocsStyleHighlighterProps {
  content: string
  notes: Note[]
  onAddNote: (selectedText: string, range: Range, color: string, noteText: string) => Promise<void>
  onUpdateNote?: (noteId: string, noteText: string) => Promise<void>
  onDeleteNote?: (noteId: string) => Promise<void>
  className?: string
}

const HIGHLIGHT_COLORS = [
  { value: '#FFEB3B', name: 'Yellow', class: 'bg-yellow-200' },
  { value: '#FFC107', name: 'Orange', class: 'bg-orange-200' },
  { value: '#4CAF50', name: 'Green', class: 'bg-green-200' },
  { value: '#2196F3', name: 'Blue', class: 'bg-blue-200' },
  { value: '#9C27B0', name: 'Purple', class: 'bg-purple-200' },
  { value: '#F44336', name: 'Red', class: 'bg-red-200' },
]

export function GoogleDocsStyleHighlighter({
  content,
  notes,
  onAddNote,
  onUpdateNote,
  onDeleteNote,
  className = ''
}: GoogleDocsStyleHighlighterProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [selection, setSelection] = useState<Range | null>(null)
  const [showCommentBubble, setShowCommentBubble] = useState(false)
  const [showColorPicker, setShowColorPicker] = useState(false)
  const [selectedColor, setSelectedColor] = useState(HIGHLIGHT_COLORS[0].value)
  const [commentText, setCommentText] = useState('')
  const [highlights, setHighlights] = useState<Highlight[]>([])
  const [activeNoteId, setActiveNoteId] = useState<string | null>(null)
  const [bubblePosition, setBubblePosition] = useState({ top: 0, left: 0 })
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null)
  const [editingText, setEditingText] = useState('')

  // Convert notes to highlights
  useEffect(() => {
    if (!containerRef.current || notes.length === 0) {
      setHighlights([])
      return
    }

    const textContent = containerRef.current.textContent || ''
    const highlightList: Highlight[] = []

    notes.forEach((note) => {
      if (note.highlightedText) {
        const start = textContent.indexOf(note.highlightedText)
        if (start !== -1) {
          highlightList.push({
            id: note.id,
            start,
            end: start + note.highlightedText.length,
            text: note.highlightedText,
            color: note.highlightColor || HIGHLIGHT_COLORS[0].value,
            noteText: note.noteText,
            noteId: note.id
          })
        }
      }
    })

    setHighlights(highlightList)
  }, [notes])

  // Apply highlights to content
  useEffect(() => {
    if (!containerRef.current) return

    const textContent = containerRef.current.textContent || ''
    let highlightedHTML = ''
    let lastIndex = 0

    // Sort highlights by start position
    const sortedHighlights = [...highlights].sort((a, b) => a.start - b.start)

    sortedHighlights.forEach((highlight) => {
      // Add text before highlight
      if (highlight.start > lastIndex) {
        highlightedHTML += escapeHtml(textContent.substring(lastIndex, highlight.start))
      }

      // Add highlighted text with comment indicator
      const highlightedText = textContent.substring(highlight.start, highlight.end)
      const hasComment = highlight.noteText && highlight.noteText.trim().length > 0
      highlightedHTML += `<mark 
        style="background-color: ${highlight.color}; padding: 2px 4px; cursor: pointer; border-radius: 2px; position: relative;" 
        data-note-id="${highlight.id}"
        data-has-comment="${hasComment}"
        class="highlight-mark ${hasComment ? 'has-comment' : ''}"
      >${escapeHtml(highlightedText)}</mark>`

      lastIndex = highlight.end
    })

    // Add remaining text
    if (lastIndex < textContent.length) {
      highlightedHTML += escapeHtml(textContent.substring(lastIndex))
    }

    containerRef.current.innerHTML = highlightedHTML

    // Add click handlers to highlights
    const marks = containerRef.current.querySelectorAll('.highlight-mark')
    marks.forEach((mark) => {
      mark.addEventListener('click', handleHighlightClick)
    })

    return () => {
      marks.forEach((mark) => {
        mark.removeEventListener('click', handleHighlightClick)
      })
    }
  }, [highlights])

  const escapeHtml = (text: string) => {
    const div = document.createElement('div')
    div.textContent = text
    return div.innerHTML
  }

  const handleMouseUp = useCallback(() => {
    const selection = window.getSelection()
    if (!selection || selection.rangeCount === 0) return

    const range = selection.getRangeAt(0)
    const selectedText = range.toString().trim()

    // Don't show bubble if clicking on existing highlight
    if (range.commonAncestorContainer.parentElement?.classList.contains('highlight-mark')) {
      return
    }

    if (selectedText.length > 0 && containerRef.current?.contains(range.commonAncestorContainer)) {
      const rect = range.getBoundingClientRect()
      const containerRect = containerRef.current.getBoundingClientRect()
      
      setSelection(range.cloneRange())
      setBubblePosition({
        top: rect.bottom - containerRect.top + 5,
        left: rect.left - containerRect.left
      })
      setShowColorPicker(true)
      setShowCommentBubble(true)
      setCommentText('')
      setSelectedColor(HIGHLIGHT_COLORS[0].value)
    } else {
      setSelection(null)
      setShowCommentBubble(false)
      setShowColorPicker(false)
    }
  }, [])

  const handleHighlightClick = (e: Event) => {
    const target = e.target as HTMLElement
    const noteId = target.getAttribute('data-note-id')
    if (noteId) {
      setActiveNoteId(noteId)
      const note = notes.find(n => n.id === noteId)
      if (note) {
        setCommentText(note.noteText || '')
      }
      
      const rect = target.getBoundingClientRect()
      const containerRect = containerRef.current?.getBoundingClientRect()
      if (containerRect) {
        setBubblePosition({
          top: rect.bottom - containerRect.top + 5,
          left: rect.left - containerRect.left
        })
      }
      setShowCommentBubble(true)
      setShowColorPicker(false)
    }
  }

  const handleAddNote = async () => {
    if (!selection) return

    const selectedText = selection.toString().trim()
    if (selectedText.length === 0) return

    try {
      await onAddNote(selectedText, selection, selectedColor, commentText)
      window.getSelection()?.removeAllRanges()
      setSelection(null)
      setShowCommentBubble(false)
      setShowColorPicker(false)
      setCommentText('')
    } catch (error) {
    }
  }

  const handleUpdateNote = async (noteId: string) => {
    if (!onUpdateNote) return
    
    try {
      await onUpdateNote(noteId, editingText)
      setEditingNoteId(null)
      setEditingText('')
    } catch (error) {
    }
  }

  const handleDeleteNote = async (noteId: string) => {
    if (!onDeleteNote) return
    
    try {
      await onDeleteNote(noteId)
      setActiveNoteId(null)
      setShowCommentBubble(false)
    } catch (error) {
    }
  }

  const activeNote = activeNoteId ? notes.find(n => n.id === activeNoteId) : null

  // Render markdown first, then apply highlights
  const [renderedContent, setRenderedContent] = useState('')

  useEffect(() => {
    // If content is markdown, we need to render it first
    // For now, we'll assume content is already HTML or plain text
    // In a real implementation, you'd use a markdown renderer here
    setRenderedContent(content)
  }, [content])

  return (
    <div className={`relative ${className}`} ref={containerRef}>
      <div
        onMouseUp={handleMouseUp}
        className="select-text prose prose-sm max-w-none"
        dangerouslySetInnerHTML={{ __html: renderedContent }}
      />

      {/* Comment Bubble - Google Docs style */}
      {showCommentBubble && (
        <div
          className="absolute z-50 bg-white rounded-lg shadow-xl border border-gray-200 min-w-[280px] max-w-[400px]"
          style={{
            top: `${bubblePosition.top}px`,
            left: `${bubblePosition.left}px`,
            transform: 'translateY(8px)'
          }}
        >
          {showColorPicker ? (
            // New comment - show color picker and comment input
            <div className="p-4">
              <div className="mb-3">
                <p className="text-xs text-gray-500 mb-2 font-medium">Selected text:</p>
                <p className="text-sm text-gray-700 italic bg-gray-50 p-2 rounded line-clamp-2">
                  {selection?.toString().trim()}
                </p>
              </div>

              <div className="mb-3">
                <p className="text-xs text-gray-500 mb-2 font-medium">Choose highlight color:</p>
                <div className="flex items-center space-x-2">
                  {HIGHLIGHT_COLORS.map((color) => (
                    <button
                      key={color.value}
                      onClick={() => setSelectedColor(color.value)}
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

              <div className="mb-3">
                <textarea
                  value={commentText}
                  onChange={(e) => setCommentText(e.target.value)}
                  placeholder="Add a comment..."
                  rows={3}
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                  autoFocus
                />
              </div>

              <div className="flex items-center justify-end space-x-2">
                <button
                  onClick={() => {
                    setShowCommentBubble(false)
                    setShowColorPicker(false)
                    setSelection(null)
                    window.getSelection()?.removeAllRanges()
                  }}
                  className="px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 rounded"
                >
                  Cancel
                </button>
                <button
                  onClick={handleAddNote}
                  className="px-4 py-1.5 text-sm bg-primary-600 text-white rounded hover:bg-primary-700"
                >
                  Comment
                </button>
              </div>
            </div>
          ) : activeNote ? (
            // Existing comment - show comment and allow editing
            <div className="p-4">
              <div className="mb-3">
                <p className="text-xs text-gray-500 mb-1 font-medium">Highlighted text:</p>
                <p className="text-sm text-gray-700 italic bg-gray-50 p-2 rounded">
                  {activeNote.highlightedText}
                </p>
              </div>

              {editingNoteId === activeNote.id ? (
                <div className="mb-3">
                  <textarea
                    value={editingText}
                    onChange={(e) => setEditingText(e.target.value)}
                    rows={3}
                    className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                    autoFocus
                  />
                  <div className="flex items-center justify-end space-x-2 mt-2">
                    <button
                      onClick={() => {
                        setEditingNoteId(null)
                        setEditingText('')
                      }}
                      className="px-3 py-1 text-xs text-gray-700 hover:bg-gray-100 rounded"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={() => handleUpdateNote(activeNote.id)}
                      className="px-3 py-1 text-xs bg-primary-600 text-white rounded hover:bg-primary-700"
                    >
                      Save
                    </button>
                  </div>
                </div>
              ) : (
                <div className="mb-3">
                  {activeNote.noteText ? (
                    <div className="bg-gray-50 p-3 rounded">
                      <p className="text-sm text-gray-700 whitespace-pre-wrap">{activeNote.noteText}</p>
                    </div>
                  ) : (
                    <p className="text-sm text-gray-400 italic">No comment added</p>
                  )}
                  <div className="flex items-center justify-end space-x-2 mt-2">
                    <button
                      onClick={() => {
                        setEditingNoteId(activeNote.id)
                        setEditingText(activeNote.noteText || '')
                      }}
                      className="px-2 py-1 text-xs text-gray-600 hover:bg-gray-100 rounded"
                    >
                      Edit
                    </button>
                    {onDeleteNote && (
                      <button
                        onClick={() => handleDeleteNote(activeNote.id)}
                        className="px-2 py-1 text-xs text-red-600 hover:bg-red-50 rounded"
                      >
                        Delete
                      </button>
                    )}
                  </div>
                </div>
              )}

              <div className="flex items-center justify-end">
                <button
                  onClick={() => {
                    setShowCommentBubble(false)
                    setActiveNoteId(null)
                  }}
                  className="px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 rounded"
                >
                  Close
                </button>
              </div>
            </div>
          ) : null}
        </div>
      )}

      {/* Click outside to close */}
      {showCommentBubble && (
        <div
          className="fixed inset-0 z-40"
          onClick={() => {
            setShowCommentBubble(false)
            setShowColorPicker(false)
            setActiveNoteId(null)
            setSelection(null)
            window.getSelection()?.removeAllRanges()
          }}
        />
      )}
    </div>
  )
}

