'use client'

import React, { useState, useEffect, useRef, useCallback } from 'react'
import { MarkdownRenderer } from './MarkdownRenderer'
import type { Note } from '@edudron/shared-utils'

interface MarkdownWithHighlightsProps {
  content: string // Markdown source content
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

export function MarkdownWithHighlights({
  content,
  notes,
  onAddNote,
  onUpdateNote,
  onDeleteNote,
  className = ''
}: MarkdownWithHighlightsProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const selectionRef = useRef<Range | null>(null)
  const [selection, setSelection] = useState<Range | null>(null)
  const [showCommentBubble, setShowCommentBubble] = useState(false)
  const [showColorPicker, setShowColorPicker] = useState(false)
  const [selectedColor, setSelectedColor] = useState(HIGHLIGHT_COLORS[0].value)
  const [commentText, setCommentText] = useState('')
  const [activeNoteId, setActiveNoteId] = useState<string | null>(null)
  const [bubblePosition, setBubblePosition] = useState({ top: 0, left: 0 })
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null)
  const [editingText, setEditingText] = useState('')
  const [selectedText, setSelectedText] = useState('')

  // Normalize text for comparison (remove extra whitespace, handle special characters)
  const normalizeText = (text: string) => {
    if (!text) return ''
    return text.replace(/\s+/g, ' ').replace(/[\u200B-\u200D\uFEFF]/g, '').trim()
  }

  // Get all text nodes from the container (excluding highlights)
  const getAllTextNodes = (container: Node): Text[] => {
    const textNodes: Text[] = []
    const walker = document.createTreeWalker(
      container,
      NodeFilter.SHOW_TEXT,
      {
        acceptNode: (node) => {
          // Skip if inside a highlight mark
          let parent = node.parentElement
          while (parent && parent !== container) {
            if (parent.classList?.contains('highlight-mark')) {
              return NodeFilter.FILTER_REJECT
            }
            parent = parent.parentElement
          }
          return NodeFilter.FILTER_ACCEPT
        }
      }
    )
    
    let node
    while (node = walker.nextNode()) {
      if (node.nodeType === Node.TEXT_NODE) {
        textNodes.push(node as Text)
      }
    }
    return textNodes
  }

  // Find markdown text in the rendered content
  // Since we store markdown source, we need to find where that markdown text appears in the rendered HTML
  const findMarkdownTextInRendered = (markdownText: string, container: Node): { node: Text; start: number; end: number } | null => {
    if (!markdownText) return null

    // Remove markdown syntax to get plain text for searching
    // This handles: **bold**, *italic*, `code`, # headers, etc.
    const plainText = markdownText
      .replace(/\*\*([^*]+)\*\*/g, '$1')  // Remove **bold**
      .replace(/\*([^*]+)\*/g, '$1')      // Remove *italic*
      .replace(/`([^`]+)`/g, '$1')        // Remove `code`
      .replace(/#+\s*/g, '')              // Remove # headers
      .replace(/\[([^\]]+)\]\([^\)]+\)/g, '$1') // Remove [link](url) -> link
      .replace(/[*_`#\[\]()]/g, '')       // Remove any remaining markdown chars
      .trim()
    
    const normalizedPlain = normalizeText(plainText)
    if (normalizedPlain.length === 0) return null
    
    // Get all text nodes
    const textNodes = getAllTextNodes(container)
    
    // Strategy 1: Try exact match of plain text
    for (const node of textNodes) {
      const nodeText = node.textContent || ''
      const index = nodeText.indexOf(plainText)
      if (index !== -1) {
        return { node, start: index, end: index + plainText.length }
      }
    }

    // Strategy 2: Normalized matching
    for (const node of textNodes) {
      const nodeText = node.textContent || ''
      const normalizedNodeText = normalizeText(nodeText)
      
      if (normalizedNodeText.includes(normalizedPlain)) {
        const normalizedIndex = normalizedNodeText.indexOf(normalizedPlain)
        
        // Map back to actual position
        let actualIndex = 0
        let normalizedPos = 0
        let actualPos = 0
        
        while (normalizedPos < normalizedIndex && actualPos < nodeText.length) {
          const char = nodeText[actualPos]
          if (/\s/.test(char)) {
            actualPos++
            while (actualPos < nodeText.length && /\s/.test(nodeText[actualPos])) {
              actualPos++
            }
          } else {
            normalizedPos++
            actualPos++
          }
        }
        
        actualIndex = actualPos
        const endIndex = Math.min(actualIndex + plainText.length, nodeText.length)
        
        return { node, start: actualIndex, end: endIndex }
      }
    }

    // Strategy 3: Word-by-word matching (for longer texts)
    const words = normalizedPlain.split(' ').filter(w => w.length > 2)
    if (words.length >= 3) {
      // Try to find first 3-5 words
      const searchPhrase = words.slice(0, Math.min(5, words.length)).join(' ')
      
      for (const node of textNodes) {
        const nodeText = node.textContent || ''
        const normalizedNodeText = normalizeText(nodeText)
        
        if (normalizedNodeText.includes(searchPhrase)) {
          const normalizedIndex = normalizedNodeText.indexOf(searchPhrase)
          
          // Map back to actual position
          let actualIndex = 0
          let normalizedPos = 0
          let actualPos = 0
          
          while (normalizedPos < normalizedIndex && actualPos < nodeText.length) {
            const char = nodeText[actualPos]
            if (/\s/.test(char)) {
              actualPos++
              while (actualPos < nodeText.length && /\s/.test(nodeText[actualPos])) {
                actualPos++
              }
            } else {
              normalizedPos++
              actualPos++
            }
          }
          
          actualIndex = actualPos
          // Estimate end - use the search phrase length plus some buffer
          const endIndex = Math.min(actualIndex + searchPhrase.length + 30, nodeText.length)
          
          return { node, start: actualIndex, end: endIndex }
        }
      }
    }

    return null
  }

  // Apply highlights to rendered markdown
  useEffect(() => {
    if (!containerRef.current) return

    // Remove existing highlights first - properly unwrap them
    const marks = containerRef.current.querySelectorAll('.highlight-mark')
    marks.forEach(mark => {
      const parent = mark.parentNode
      if (parent) {
        // Replace the mark with its text content
        const textNode = document.createTextNode(mark.textContent || '')
        parent.replaceChild(textNode, mark)
        // Normalize to merge adjacent text nodes
        parent.normalize()
      }
    })

    if (notes.length === 0) return

    // Wait for markdown to fully render
    const timeoutId = setTimeout(() => {
      if (!containerRef.current) return

      console.log('[MarkdownWithHighlights] Applying highlights for', notes.length, 'notes')
      notes.forEach((note) => {
        if (!note.highlightedText) {
          console.warn('[MarkdownWithHighlights] Note missing highlightedText:', note.id)
          return
        }

        console.log('[MarkdownWithHighlights] Searching for markdown text:', note.highlightedText.substring(0, 50))

        // Find the markdown text in the rendered content
        const match = findMarkdownTextInRendered(note.highlightedText, containerRef.current!)
        
        if (match) {
          const { node, start, end } = match
          
          const range = document.createRange()
          try {
            range.setStart(node, start)
            range.setEnd(node, end)
          } catch (e) {
            console.warn('[MarkdownWithHighlights] Failed to create range:', e)
            return
          }

          const mark = document.createElement('mark')
          mark.className = 'highlight-mark'
          mark.setAttribute('data-note-id', note.id)
          mark.setAttribute('data-has-comment', note.noteText ? 'true' : 'false')
          mark.style.backgroundColor = note.highlightColor || HIGHLIGHT_COLORS[0].value
          mark.style.padding = '2px 4px'
          mark.style.cursor = 'pointer'
          mark.style.borderRadius = '2px'
          mark.style.position = 'relative'
          
          if (note.noteText) {
            mark.classList.add('has-comment')
          }

          try {
            range.surroundContents(mark)
          } catch (e) {
            // If surroundContents fails, try extractContents approach
            try {
              const contents = range.extractContents()
              mark.appendChild(contents)
              range.insertNode(mark)
            } catch (e2) {
              console.warn('[MarkdownWithHighlights] Failed to apply highlight for note:', note.id, e2)
              return
            }
          }

          mark.addEventListener('click', (e) => {
            e.stopPropagation()
            handleHighlightClick(note.id)
          })
          console.log('[MarkdownWithHighlights] Successfully applied highlight for note:', note.id)
        } else {
          console.warn('[MarkdownWithHighlights] Could not find text for note:', note.id, 'Text:', note.highlightedText?.substring(0, 50))
        }
      })
    }, 200) // Small delay to ensure markdown is fully rendered

    return () => clearTimeout(timeoutId)
  }, [notes, content])

  // Extract plain text from a range, handling markdown-rendered content
  const extractPlainText = (range: Range): string => {
    // Clone the range to avoid modifying the original
    const clonedRange = range.cloneRange()
    
    // Get the text content (this gives us the rendered text without HTML tags)
    let text = clonedRange.toString()
    
    // If the range spans multiple nodes, we need to extract text more carefully
    // to handle cases where markdown has been rendered to HTML
    const contents = clonedRange.cloneContents()
    const tempDiv = document.createElement('div')
    tempDiv.appendChild(contents)
    
    // Get text content which strips all HTML and gives us plain text
    const plainText = tempDiv.textContent || tempDiv.innerText || text
    
    // Normalize whitespace
    return plainText.replace(/\s+/g, ' ').trim()
  }

  const handleMouseUp = useCallback(() => {
    const selection = window.getSelection()
    if (!selection || selection.rangeCount === 0) return

    const range = selection.getRangeAt(0)
    
    // Don't show bubble if clicking on existing highlight
    if (range.commonAncestorContainer.parentElement?.classList.contains('highlight-mark')) {
      return
    }

    // Extract plain text from the rendered content
    const renderedText = extractPlainText(range)
    
    // Find the corresponding markdown source text
    const markdownText = findMarkdownSourceText(renderedText, content) || renderedText

    if (renderedText.length > 0 && containerRef.current?.contains(range.commonAncestorContainer)) {
      // Store the range in both state and ref for persistence
      const clonedRange = range.cloneRange()
      selectionRef.current = clonedRange
      setSelection(clonedRange)
      setSelectedText(markdownText) // Store the markdown source text for easier matching
      
      const rect = range.getBoundingClientRect()
      const containerRect = containerRef.current.getBoundingClientRect()
      
      setBubblePosition({
        top: rect.bottom - containerRect.top + 5,
        left: rect.left - containerRect.left
      })
      setShowColorPicker(true)
      setShowCommentBubble(true)
      setCommentText('')
      setSelectedColor(HIGHLIGHT_COLORS[0].value)
      
      // Keep the selection visible by not clearing it immediately
      // We'll only clear it when the user cancels or saves
    } else {
      // Only clear if clicking outside the container
      if (!containerRef.current?.contains(range.commonAncestorContainer)) {
        selectionRef.current = null
        setSelection(null)
        setShowCommentBubble(false)
        setShowColorPicker(false)
      }
    }
  }, [])

  const handleHighlightClick = (noteId: string) => {
    setActiveNoteId(noteId)
    const note = notes.find(n => n.id === noteId)
    if (note) {
      setCommentText(note.noteText || '')
    }
    
    const mark = containerRef.current?.querySelector(`[data-note-id="${noteId}"]`)
    if (mark) {
      const rect = mark.getBoundingClientRect()
      const containerRect = containerRef.current?.getBoundingClientRect()
      if (containerRect) {
        setBubblePosition({
          top: rect.bottom - containerRect.top + 5,
          left: rect.left - containerRect.left
        })
      }
    }
    setShowCommentBubble(true)
    setShowColorPicker(false)
  }

  const handleAddNote = async () => {
    const currentSelection = selectionRef.current || selection
    if (!currentSelection) return

    // Use the markdown source text (stored in selectedText)
    const text = selectedText
    if (!text || text.length === 0) return

    try {
      // Save the markdown source text for easier matching later
      await onAddNote(text, currentSelection, selectedColor, commentText)
      // Clear selection only after successful save
      window.getSelection()?.removeAllRanges()
      selectionRef.current = null
      setSelection(null)
      setSelectedText('')
      setShowCommentBubble(false)
      setShowColorPicker(false)
      setCommentText('')
    } catch (error) {
      console.error('Failed to add note:', error)
    }
  }

  const handleUpdateNote = async (noteId: string) => {
    if (!onUpdateNote) return
    
    try {
      await onUpdateNote(noteId, editingText)
      setEditingNoteId(null)
      setEditingText('')
    } catch (error) {
      console.error('Failed to update note:', error)
    }
  }

  const handleDeleteNote = async (noteId: string) => {
    if (!onDeleteNote) return
    
    try {
      await onDeleteNote(noteId)
      setActiveNoteId(null)
      setShowCommentBubble(false)
    } catch (error) {
      console.error('Failed to delete note:', error)
    }
  }

  const activeNote = activeNoteId ? notes.find(n => n.id === activeNoteId) : null

  return (
    <div className={`relative ${className}`}>
      <div
        ref={containerRef}
        onMouseUp={handleMouseUp}
        className="select-text"
      >
        <MarkdownRenderer content={content} />
      </div>

      {/* Comment Bubble - Google Docs style */}
      {showCommentBubble && (
        <>
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
                    {selectedText || selection?.toString().trim()}
                  </p>
                </div>

                <div className="mb-3">
                  <p className="text-xs text-gray-500 mb-2 font-medium">Choose highlight color:</p>
                  <div className="flex items-center space-x-2">
                    {HIGHLIGHT_COLORS.map((color) => (
                      <button
                        key={color.value}
                        type="button"
                        onClick={(e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          setSelectedColor(color.value)
                          // Preserve selection when changing color
                          if (selectionRef.current) {
                            try {
                              const sel = window.getSelection()
                              if (sel) {
                                sel.removeAllRanges()
                                sel.addRange(selectionRef.current.cloneRange())
                              }
                            } catch (err) {
                              // Selection might be invalid, that's okay
                            }
                          }
                        }}
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
                    onChange={(e) => {
                      setCommentText(e.target.value)
                      // Preserve selection when typing
                      if (selectionRef.current) {
                        try {
                          const sel = window.getSelection()
                          if (sel && sel.rangeCount === 0) {
                            sel.addRange(selectionRef.current.cloneRange())
                          }
                        } catch (err) {
                          // Selection might be invalid, that's okay
                        }
                      }
                    }}
                    onFocus={() => {
                      // Restore selection when textarea is focused
                      if (selectionRef.current) {
                        try {
                          const sel = window.getSelection()
                          if (sel) {
                            sel.removeAllRanges()
                            sel.addRange(selectionRef.current.cloneRange())
                          }
                        } catch (err) {
                          // Selection might be invalid, that's okay
                        }
                      }
                    }}
                    placeholder="Add a comment..."
                    rows={3}
                    className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                    autoFocus
                  />
                </div>

                <div className="flex items-center justify-end space-x-2">
                  <button
                    onClick={() => {
                      // Clear selection on cancel
                      window.getSelection()?.removeAllRanges()
                      selectionRef.current = null
                      setSelection(null)
                      setSelectedText('')
                      setShowCommentBubble(false)
                      setShowColorPicker(false)
                      setCommentText('')
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

          {/* Click outside to close */}
          <div
            className="fixed inset-0 z-40"
            onClick={() => {
              // Only clear if clicking outside (not on the bubble itself)
              window.getSelection()?.removeAllRanges()
              selectionRef.current = null
              setSelection(null)
              setSelectedText('')
              setShowCommentBubble(false)
              setShowColorPicker(false)
              setActiveNoteId(null)
              setCommentText('')
            }}
          />
        </>
      )}
    </div>
  )
}

