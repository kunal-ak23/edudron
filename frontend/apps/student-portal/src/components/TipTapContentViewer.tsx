'use client'

import { useState, useEffect, useRef, useCallback } from 'react'
import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import { ResizableImage } from '@kunal-ak23/edudron-shared-utils'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import { Markdown } from 'tiptap-markdown'
import { HighlightMark } from '@kunal-ak23/edudron-shared-utils'
import '@kunal-ak23/edudron-shared-utils/tiptap/editor-styles.css'
import type { Note } from '@kunal-ak23/edudron-shared-utils'

interface TipTapContentViewerProps {
  content: string
  notes: Note[]
  onAddNote: (selectedText: string, range: Range, color: string, noteText: string) => Promise<void>
  onUpdateNote?: (noteId: string, noteText: string) => Promise<void>
  onDeleteNote?: (noteId: string) => Promise<void>
  className?: string
}

const HIGHLIGHT_COLORS = [
  { value: '#FFEB3B', name: 'Yellow' },
  { value: '#FFC107', name: 'Orange' },
  { value: '#4CAF50', name: 'Green' },
  { value: '#2196F3', name: 'Blue' },
  { value: '#9C27B0', name: 'Purple' },
  { value: '#F44336', name: 'Red' },
]

/**
 * Normalize text for fuzzy matching:
 * - Remove zero-width chars
 * - Convert smart quotes to straight quotes
 * - Strip Unicode list markers (bullets, etc.)
 * - Strip numbered list prefixes (1. 2. etc.)
 * - Collapse whitespace, trim, lowercase
 */
function normalize(text: string): string {
  return text
    // Remove zero-width characters
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    // Smart quotes → straight quotes
    .replace(/[\u2018\u2019\u201A\u201B]/g, "'")
    .replace(/[\u201C\u201D\u201E\u201F]/g, '"')
    // Remove common Unicode list markers that browsers may inject
    .replace(/[\u2022\u2023\u25E6\u2043\u2219\u25AA\u25CF\u25CB\u25A0\u25A1\u2013\u2014]/g, '')
    // Remove numbered list prefixes at start of lines (e.g. "1. ", "12. ")
    .replace(/(?:^|\n)\d+\.\s+/g, ' ')
    // Collapse all whitespace (including tabs, newlines) to single space
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase()
}

/** Strip common markdown syntax to get plain text for matching */
function stripMarkdown(md: string): string {
  let t = md
  t = t.replace(/\*\*([^*]+)\*\*/g, '$1')
  t = t.replace(/\*([^*]+)\*/g, '$1')
  t = t.replace(/__([^_]+)__/g, '$1')
  t = t.replace(/_([^_]+)_/g, '$1')
  t = t.replace(/`([^`]+)`/g, '$1')
  t = t.replace(/~~([^~]+)~~/g, '$1')
  t = t.replace(/#+\s+/g, '')
  t = t.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
  t = t.replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
  return t
}

/**
 * Search the ProseMirror document for `needle` text and return `{ from, to }` positions.
 * Handles cross-block matching (heading → paragraph → list items).
 *
 * Algorithm:
 * 1. Collect all text nodes with their document positions
 * 2. Build combined text with a space separator between nodes
 * 3. Build a direct charIndex→docPos mapping array
 * 4. Normalize both combined text and needle, then indexOf
 * 5. Map normalized match positions back to document positions via a
 *    normalized-index→combined-index mapping built during normalization
 */
function findTextInDoc(doc: any, needle: string): { from: number; to: number } | null {
  const stripped = stripMarkdown(needle)
  const target = normalize(stripped)
  if (!target) return null

  // 1. Collect all text nodes with their positions
  const texts: { text: string; pos: number }[] = []
  doc.descendants((node: any, pos: number) => {
    if (node.isText) {
      texts.push({ text: node.text!, pos })
    }
  })

  if (texts.length === 0) return null

  // 2. Build combined text with a direct charIndex→docPos mapping
  // Each character in `combined` maps to a document position
  const charToDocPos: number[] = [] // charToDocPos[i] = docPos for combined[i]
  let combined = ''

  for (let ti = 0; ti < texts.length; ti++) {
    const t = texts[ti]
    for (let i = 0; i < t.text.length; i++) {
      charToDocPos.push(t.pos + i)
      combined += t.text[i]
    }
    // Add a space between text nodes to handle cross-node matching
    // Map it to the position right after the text node ends
    if (ti < texts.length - 1) {
      charToDocPos.push(t.pos + t.text.length)
      combined += ' '
    }
  }

  // 3. Normalize the combined text and build a mapping:
  //    normIndex → combinedIndex (so we can map back from a match in normalized text)
  const normToCombIdx: number[] = []
  const normalizedChars: string[] = []

  let i = 0
  // Skip leading whitespace (to match trim() behavior)
  while (i < combined.length && /\s/.test(combined[i])) i++

  // Find the end (trim trailing whitespace)
  let end = combined.length
  while (end > i && /\s/.test(combined[end - 1])) end--

  let inWhitespace = false
  while (i < end) {
    const ch = combined[i]
    // Skip zero-width characters
    if (/[\u200B-\u200D\uFEFF]/.test(ch)) { i++; continue }

    if (/\s/.test(ch)) {
      if (!inWhitespace) {
        // Emit a single space for a whitespace run
        normToCombIdx.push(i)
        normalizedChars.push(' ')
        inWhitespace = true
      }
      i++
    } else {
      normToCombIdx.push(i)
      normalizedChars.push(ch.toLowerCase())
      inWhitespace = false
      i++
    }
  }

  const normalizedCombined = normalizedChars.join('')

  // 4. Find the target in the normalized combined text
  let idx = normalizedCombined.indexOf(target)

  // Fallback: strip all non-alphanumeric and retry
  if (idx === -1) {
    const aggressiveNorm = (s: string) => s.replace(/[^a-z0-9]/g, '')
    const aggressiveTarget = aggressiveNorm(target)
    const aggressiveCombined = aggressiveNorm(normalizedCombined)
    if (aggressiveTarget && aggressiveCombined.includes(aggressiveTarget)) {
      // Find where the aggressive match starts in the normalizedCombined
      // by walking both strings character by character
      let ai = 0, ni2 = 0
      const aggressiveIdx = aggressiveCombined.indexOf(aggressiveTarget)
      // Map aggressiveIdx back to normalizedCombined index
      let agCount = 0
      for (let ci = 0; ci < normalizedCombined.length && agCount <= aggressiveIdx + aggressiveTarget.length; ci++) {
        if (/[a-z0-9]/.test(normalizedCombined[ci])) {
          if (agCount === aggressiveIdx) { idx = ci; break }
          agCount++
        }
      }
      // For the aggressive fallback, also need to find the length in normalized space
      if (idx !== -1) {
        // Find the end position
        let matchedAlphaNum = 0
        let endIdx = idx
        for (let ci = idx; ci < normalizedCombined.length && matchedAlphaNum < aggressiveTarget.length; ci++) {
          endIdx = ci + 1
          if (/[a-z0-9]/.test(normalizedCombined[ci])) {
            matchedAlphaNum++
          }
        }
        // Adjust target for position mapping (use the span in normalizedCombined)
        const matchLen = endIdx - idx
        // Map back using the same normToCombIdx
        if (idx >= normToCombIdx.length) return null
        const startCombinedIdx = normToCombIdx[idx]
        const endNormIdx = idx + matchLen - 1
        const endCombinedIdx = endNormIdx < normToCombIdx.length
          ? normToCombIdx[endNormIdx] + 1
          : combined.length

        const from = charToDocPos[startCombinedIdx]
        const lastIdx = Math.min(endCombinedIdx - 1, charToDocPos.length - 1)
        const to = charToDocPos[lastIdx] + 1

        if (from == null || to == null || from >= to) return null
        return { from, to }
      }
    }

    // Could not match at all
    if (typeof console !== 'undefined' && console.warn) {
      console.warn(
        '[TipTapContentViewer] Could not find note text in document.',
        '\n  needle:', target.slice(0, 100),
        '\n  doc:', normalizedCombined.slice(0, 200)
      )
    }
    return null
  }

  // 5. Map normalized match positions back to document positions
  if (idx >= normToCombIdx.length) return null
  const startCombinedIdx = normToCombIdx[idx]

  const endNormIdx = idx + target.length - 1
  const endCombinedIdx = endNormIdx < normToCombIdx.length
    ? normToCombIdx[endNormIdx] + 1
    : combined.length

  // Map combined indices to document positions
  if (startCombinedIdx >= charToDocPos.length) return null
  const from = charToDocPos[startCombinedIdx]
  const lastIdx = Math.min(endCombinedIdx - 1, charToDocPos.length - 1)
  if (lastIdx < 0) return null
  const to = charToDocPos[lastIdx] + 1

  if (from >= to) return null
  return { from, to }
}

export function TipTapContentViewer({
  content,
  notes,
  onAddNote,
  onUpdateNote,
  onDeleteNote,
  className = '',
}: TipTapContentViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [showCommentBubble, setShowCommentBubble] = useState(false)
  const [showColorPicker, setShowColorPicker] = useState(false)
  const [selectedColor, setSelectedColor] = useState(HIGHLIGHT_COLORS[0].value)
  const [commentText, setCommentText] = useState('')
  const [activeNoteId, setActiveNoteId] = useState<string | null>(null)
  const [bubblePosition, setBubblePosition] = useState({ top: 0, left: 0 })
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null)
  const [editingText, setEditingText] = useState('')
  const [selectedText, setSelectedText] = useState('')
  const selectionRangeRef = useRef<Range | null>(null)

  const editor = useEditor({
    immediatelyRender: false,
    editable: false,
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3, 4] },
      }),
      Link.configure({
        openOnClick: true,
        HTMLAttributes: {
          class: 'text-primary-600 hover:text-primary-700 underline font-medium',
          target: '_blank',
          rel: 'noopener noreferrer',
        },
      }),
      ResizableImage.configure({
        HTMLAttributes: { class: 'max-w-full h-auto rounded' },
      }),
      Table.configure({ resizable: false }),
      TableRow,
      TableHeader,
      TableCell,
      Markdown.configure({ html: true }),
      HighlightMark,
    ],
    content: '',
    editorProps: {
      attributes: {
        class: `prose prose-lg max-w-none ${className}`,
      },
    },
  })

  // Load content
  useEffect(() => {
    if (!editor || !content) return
    editor.commands.setContent(content)
  }, [content, editor])

  // Apply highlight marks from notes
  useEffect(() => {
    if (!editor || !content) return

    // Small delay to let content render first
    const timer = setTimeout(() => {
      if (!editor || editor.isDestroyed) return

      const { doc, tr } = editor.state
      let transaction = tr

      // Remove existing highlight marks first
      doc.descendants((node, pos) => {
        if (node.isText) {
          const highlightMark = node.marks.find(m => m.type.name === 'highlightMark')
          if (highlightMark) {
            transaction = transaction.removeMark(pos, pos + node.nodeSize, editor.schema.marks.highlightMark)
          }
        }
      })

      // Apply highlights for each note
      for (const note of notes) {
        if (!note.highlightedText) continue

        const match = findTextInDoc(doc, note.highlightedText)
        if (!match) continue

        const mark = editor.schema.marks.highlightMark.create({
          id: note.id,
          color: note.highlightColor || HIGHLIGHT_COLORS[0].value,
          opacity: 0.4,
        })

        transaction = transaction.addMark(match.from, match.to, mark)
      }

      if (transaction.steps.length > 0) {
        editor.view.dispatch(transaction)
      }
    }, 100)

    return () => clearTimeout(timer)
  }, [notes, content, editor])

  // Handle text selection for creating highlights
  const handleMouseUp = useCallback(() => {
    if (!editor || !containerRef.current) return

    const sel = window.getSelection()
    if (!sel || sel.rangeCount === 0) return

    const range = sel.getRangeAt(0)
    const text = range.toString().trim()

    if (text.length === 0) return
    if (!containerRef.current.contains(range.commonAncestorContainer)) return

    // Don't show bubble if clicking on existing highlight
    const parent = range.commonAncestorContainer.parentElement
    if (parent?.closest('mark[data-highlight]')) return

    selectionRangeRef.current = range.cloneRange()
    setSelectedText(text)

    const rect = range.getBoundingClientRect()
    const containerRect = containerRef.current.getBoundingClientRect()

    setBubblePosition({
      top: rect.bottom - containerRect.top + 5,
      left: Math.max(0, rect.left - containerRect.left),
    })
    setShowColorPicker(true)
    setShowCommentBubble(true)
    setCommentText('')
    setSelectedColor(HIGHLIGHT_COLORS[0].value)
  }, [editor])

  // Handle click on existing highlight
  useEffect(() => {
    if (!containerRef.current) return

    const handler = (e: MouseEvent) => {
      const mark = (e.target as HTMLElement).closest?.('mark[data-highlight]')
      if (!mark) return

      e.stopPropagation()
      const noteId = mark.getAttribute('data-note-id') || mark.getAttribute('data-highlight-id')
      if (!noteId) return

      setActiveNoteId(noteId)
      const note = notes.find(n => n.id === noteId)
      if (note) setCommentText(note.noteText || '')

      const rect = mark.getBoundingClientRect()
      const containerRect = containerRef.current!.getBoundingClientRect()
      setBubblePosition({
        top: rect.bottom - containerRect.top + 5,
        left: Math.max(0, rect.left - containerRect.left),
      })
      setShowCommentBubble(true)
      setShowColorPicker(false)
    }

    containerRef.current.addEventListener('click', handler)
    return () => containerRef.current?.removeEventListener('click', handler)
  }, [notes])

  const handleAddNote = async () => {
    if (!selectedText || !selectionRangeRef.current) return

    try {
      await onAddNote(selectedText, selectionRangeRef.current, selectedColor, commentText)
      window.getSelection()?.removeAllRanges()
      selectionRangeRef.current = null
      setSelectedText('')
      setShowCommentBubble(false)
      setShowColorPicker(false)
      setCommentText('')
    } catch (error) {
      // error handled by parent
    }
  }

  const handleUpdateNote = async (noteId: string) => {
    if (!onUpdateNote) return
    try {
      await onUpdateNote(noteId, editingText)
      setEditingNoteId(null)
      setEditingText('')
    } catch (error) {
      // error handled by parent
    }
  }

  const handleDeleteNote = async (noteId: string) => {
    if (!onDeleteNote) return
    try {
      await onDeleteNote(noteId)
      setActiveNoteId(null)
      setShowCommentBubble(false)
    } catch (error) {
      // error handled by parent
    }
  }

  const closeBubble = () => {
    window.getSelection()?.removeAllRanges()
    selectionRangeRef.current = null
    setSelectedText('')
    setShowCommentBubble(false)
    setShowColorPicker(false)
    setActiveNoteId(null)
    setCommentText('')
  }

  const activeNote = activeNoteId ? notes.find(n => n.id === activeNoteId) : null

  if (!content) return null

  return (
    <div className={`relative ${className}`}>
      <div ref={containerRef} onMouseUp={handleMouseUp} className="select-text">
        {editor ? (
          <EditorContent editor={editor} />
        ) : (
          <div className="prose prose-lg max-w-none">
            <p className="text-gray-400">Loading...</p>
          </div>
        )}
      </div>

      {/* Comment Bubble — same UI as MarkdownWithHighlights */}
      {showCommentBubble && (
        <>
          <div
            className="absolute z-50 bg-white rounded-lg shadow-xl border border-gray-200 min-w-[280px] max-w-[400px]"
            style={{
              top: `${bubblePosition.top}px`,
              left: `${bubblePosition.left}px`,
              transform: 'translateY(8px)',
            }}
          >
            {showColorPicker ? (
              <div className="p-4">
                <div className="mb-3">
                  <p className="text-xs text-gray-500 mb-2 font-medium">Selected text:</p>
                  <p className="text-sm text-gray-700 italic bg-gray-50 p-2 rounded line-clamp-2">
                    {selectedText}
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
                    onChange={(e) => setCommentText(e.target.value)}
                    placeholder="Add a comment..."
                    rows={3}
                    className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                    autoFocus
                  />
                </div>

                <div className="flex items-center justify-end space-x-2">
                  <button
                    onClick={closeBubble}
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
          <div className="fixed inset-0 z-40" onClick={closeBubble} />
        </>
      )}
    </div>
  )
}
