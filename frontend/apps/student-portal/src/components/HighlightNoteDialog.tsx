'use client'

import React, { useState } from 'react'
import { Button } from '@edudron/ui-components'

interface HighlightNoteDialogProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (color: string, noteText: string) => Promise<void>
  selectedText: string
  context?: string
}

const HIGHLIGHT_COLORS = [
  { value: '#FFEB3B', name: 'Yellow', class: 'bg-yellow-200' },
  { value: '#FFC107', name: 'Orange', class: 'bg-orange-200' },
  { value: '#4CAF50', name: 'Green', class: 'bg-green-200' },
  { value: '#2196F3', name: 'Blue', class: 'bg-blue-200' },
  { value: '#9C27B0', name: 'Purple', class: 'bg-purple-200' },
  { value: '#F44336', name: 'Red', class: 'bg-red-200' },
]

export function HighlightNoteDialog({
  isOpen,
  onClose,
  onSubmit,
  selectedText,
  context
}: HighlightNoteDialogProps) {
  const [selectedColor, setSelectedColor] = useState(HIGHLIGHT_COLORS[0].value)
  const [noteText, setNoteText] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (!isOpen) return null

  const handleSubmit = async () => {
    setIsSubmitting(true)
    try {
      await onSubmit(selectedColor, noteText.trim())
      setNoteText('')
      setSelectedColor(HIGHLIGHT_COLORS[0].value)
      onClose()
    } catch (error) {
      console.error('Failed to save note:', error)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-30 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">Add Note</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 font-semibold text-xl leading-none"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="px-6 py-6">
          {/* Selected Text Preview */}
          <div className="mb-4 p-3 bg-gray-50 rounded-lg border border-gray-200">
            <p className="text-xs text-gray-500 mb-1">Selected text:</p>
            <p className="text-sm text-gray-700 italic line-clamp-3">{selectedText}</p>
          </div>

          {/* Color Selection */}
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Highlight Color
            </label>
            <div className="flex items-center space-x-2">
              {HIGHLIGHT_COLORS.map((color) => (
                <button
                  key={color.value}
                  onClick={() => setSelectedColor(color.value)}
                  className={`w-10 h-10 rounded-full border-2 transition-all ${
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

          {/* Note Textarea */}
          <div className="mb-6">
            <label htmlFor="noteText" className="block text-sm font-medium text-gray-700 mb-2">
              Add a note (optional)
            </label>
            <textarea
              id="noteText"
              value={noteText}
              onChange={(e) => setNoteText(e.target.value)}
              placeholder="Add your thoughts or notes about this highlight..."
              rows={4}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
            />
          </div>

          {/* Action Buttons */}
          <div className="flex items-center space-x-3">
            <Button
              onClick={onClose}
              variant="outline"
              className="flex-1"
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              onClick={handleSubmit}
              disabled={isSubmitting}
              className="flex-1"
            >
              {isSubmitting ? 'Saving...' : 'Save Note'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

