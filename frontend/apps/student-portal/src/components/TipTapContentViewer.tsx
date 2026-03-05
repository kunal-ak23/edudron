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

/** Normalize text for fuzzy matching: collapse whitespace, strip zero-width chars, lowercase */
function normalize(text: string): string {
  return text
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
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
 * Searches the document's text content, matching via normalised text.
 */
function findTextInDoc(doc: any, needle: string): { from: number; to: number } | null {
  const stripped = stripMarkdown(needle)
  const target = normalize(stripped)
  if (!target) return null

  // Collect all text nodes with their positions
  const texts: { text: string; pos: number }[] = []
  doc.descendants((node: any, pos: number) => {
    if (node.isText) {
      texts.push({ text: node.text!, pos })
    }
  })

  // Build a combined text with position mapping
  let combined = ''
  const posMap: { charIndex: number; docPos: number }[] = []

  for (const t of texts) {
    for (let i = 0; i < t.text.length; i++) {
      posMap.push({ charIndex: combined.length, docPos: t.pos + i })
      combined += t.text[i]
    }
    // Add a space between text nodes to handle cross-node matching
    posMap.push({ charIndex: combined.length, docPos: t.pos + t.text.length })
    combined += ' '
  }

  const normalizedCombined = normalize(combined)

  // Find the target in the normalised combined text
  const idx = normalizedCombined.indexOf(target)
  if (idx === -1) return null

  // Map normalized positions back to combined positions
  // Build a mapping from normalised index -> combined index
  let ni = 0
  let ci = 0
  const normToCombined: number[] = []

  while (ci < combined.length) {
    const ch = combined[ci]
    if (/[\u200B-\u200D\uFEFF]/.test(ch)) {
      ci++
      continue
    }
    // Handle whitespace collapsing: multiple spaces -> single space in normalised
    if (/\s/.test(ch)) {
      normToCombined.push(ci)
      ni++
      ci++
      while (ci < combined.length && /\s/.test(combined[ci])) ci++
    } else {
      normToCombined.push(ci)
      ni++
      ci++
    }
  }

  if (idx >= normToCombined.length) return null
  const startCombinedIdx = normToCombined[idx]
  const endNormIdx = idx + target.length - 1
  const endCombinedIdx = endNormIdx < normToCombined.length ? normToCombined[endNormIdx] + 1 : combined.length

  // Map combined indices back to document positions
  const startEntry = posMap.find(e => e.charIndex >= startCombinedIdx)
  const endEntry = [...posMap].reverse().find(e => e.charIndex < endCombinedIdx)

  if (!startEntry || !endEntry) return null

  const from = startEntry.docPos
  const to = endEntry.docPos + 1

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
