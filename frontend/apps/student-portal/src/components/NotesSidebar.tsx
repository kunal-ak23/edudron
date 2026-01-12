'use client'

import React, { useState } from 'react'
import type { Note } from '@edudron/shared-utils'
import { DeleteConfirmDialog } from './DeleteConfirmDialog'

interface NotesSidebarProps {
  notes: Note[]
  isOpen: boolean
  onClose: () => void
  onNoteClick?: (noteId: string) => void
  onDeleteNote?: (noteId: string) => Promise<void>
  onUpdateNote?: (noteId: string, noteText: string) => Promise<void>
}

const HIGHLIGHT_COLORS = [
  { value: '#FFEB3B', name: 'Yellow' },
  { value: '#FFC107', name: 'Orange' },
  { value: '#4CAF50', name: 'Green' },
  { value: '#2196F3', name: 'Blue' },
  { value: '#9C27B0', name: 'Purple' },
  { value: '#F44336', name: 'Red' },
]

export function NotesSidebar({
  notes,
  isOpen,
  onClose,
  onNoteClick,
  onDeleteNote,
  onUpdateNote
}: NotesSidebarProps) {
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null)
  const [editingText, setEditingText] = useState('')
  const [deleteNoteId, setDeleteNoteId] = useState<string | null>(null)

  const handleEdit = (note: Note) => {
    setEditingNoteId(note.id)
    setEditingText(note.noteText || '')
  }

  const handleSave = async (noteId: string) => {
    if (!onUpdateNote) return
    
    try {
      await onUpdateNote(noteId, editingText)
      setEditingNoteId(null)
      setEditingText('')
    } catch (error) {
      console.error('Failed to update note:', error)
    }
  }

  const handleDeleteClick = (noteId: string) => {
    setDeleteNoteId(noteId)
  }

  const handleDeleteConfirm = async () => {
    if (!onDeleteNote || !deleteNoteId) return
    
    try {
      await onDeleteNote(deleteNoteId)
      setDeleteNoteId(null)
    } catch (error) {
      console.error('Failed to delete note:', error)
      // Keep dialog open on error so user can retry
    }
  }

  const getColorName = (colorValue: string) => {
    return HIGHLIGHT_COLORS.find(c => c.value === colorValue)?.name || 'Unknown'
  }

  if (!isOpen) return null

  return (
    <div className="fixed right-0 top-0 h-full w-96 bg-white shadow-2xl z-50 flex flex-col border-l border-gray-200">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
        <h2 className="text-lg font-semibold text-gray-900">Notes & Highlights</h2>
        <button
          onClick={onClose}
          className="text-gray-400 hover:text-gray-600"
          aria-label="Close"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Notes List */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {notes.length === 0 ? (
          <div className="text-center py-12">
            <svg className="w-16 h-16 mx-auto text-gray-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
            </svg>
            <p className="text-gray-500 text-sm">No notes yet</p>
            <p className="text-gray-400 text-xs mt-1">Select text to add a note</p>
          </div>
        ) : (
          <div className="space-y-4">
            {notes.map((note) => (
              <div
                key={note.id}
                className="border border-gray-200 rounded-lg p-4 hover:border-gray-300 transition-colors cursor-pointer"
                onClick={() => onNoteClick?.(note.id)}
              >
                {/* Highlighted Text Preview */}
                <div className="mb-3">
                  <div
                    className="inline-block px-2 py-1 rounded text-sm"
                    style={{ backgroundColor: note.highlightColor || '#FFEB3B' }}
                  >
                    <span className="text-gray-800 font-medium">
                      {note.highlightedText?.substring(0, 100)}
                      {note.highlightedText && note.highlightedText.length > 100 ? '...' : ''}
                    </span>
                  </div>
                  <span className="ml-2 text-xs text-gray-500">
                    {getColorName(note.highlightColor || '#FFEB3B')}
                  </span>
                </div>

                {/* Note Text */}
                {editingNoteId === note.id ? (
                  <div>
                    <textarea
                      value={editingText}
                      onChange={(e) => setEditingText(e.target.value)}
                      rows={3}
                      className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                      onClick={(e) => e.stopPropagation()}
                      autoFocus
                    />
                    <div className="flex items-center justify-end space-x-2 mt-2">
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          setEditingNoteId(null)
                          setEditingText('')
                        }}
                        className="px-3 py-1 text-xs text-gray-700 hover:bg-gray-100 rounded"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          handleSave(note.id)
                        }}
                        className="px-3 py-1 text-xs bg-primary-600 text-white rounded hover:bg-primary-700"
                      >
                        Save
                      </button>
                    </div>
                  </div>
                ) : (
                  <div>
                    {note.noteText ? (
                      <p className="text-sm text-gray-700 whitespace-pre-wrap">{note.noteText}</p>
                    ) : (
                      <p className="text-sm text-gray-400 italic">No comment added</p>
                    )}
                    <div className="flex items-center justify-end space-x-2 mt-2">
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          handleEdit(note)
                        }}
                        className="px-2 py-1 text-xs text-gray-600 hover:bg-gray-100 rounded"
                      >
                        Edit
                      </button>
                      {onDeleteNote && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation()
                            handleDeleteClick(note.id)
                          }}
                          className="px-2 py-1 text-xs text-red-600 hover:bg-red-50 rounded"
                        >
                          Delete
                        </button>
                      )}
                    </div>
                  </div>
                )}

                {/* Timestamp */}
                <div className="mt-2 text-xs text-gray-400">
                  {new Date(note.createdAt).toLocaleDateString()} at{' '}
                  {new Date(note.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        isOpen={deleteNoteId !== null}
        onClose={() => setDeleteNoteId(null)}
        onConfirm={handleDeleteConfirm}
        title="Delete Note"
        message="Are you sure you want to delete this note? This action cannot be undone."
      />
    </div>
  )
}

