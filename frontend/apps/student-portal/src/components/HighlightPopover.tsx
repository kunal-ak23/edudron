/**
 * HighlightPopover: UI component for adding/editing highlights and notes
 */

'use client'

import React, { useState, useEffect, useRef } from 'react'

export interface HighlightPopoverProps {
  isOpen: boolean
  position: { top: number; left: number }
  selectedText: string
  selectedColor: string
  selectedOpacity?: number
  noteText?: string
  highlightColors: Array<{ value: string; name: string }>
  onColorChange: (color: string) => void
  onOpacityChange?: (opacity: number) => void
  onNoteChange: (noteText: string) => void
  onSave: () => void
  onCancel: () => void
  mode?: 'create' | 'edit'
}

export function HighlightPopover({
  isOpen,
  position,
  selectedText,
  selectedColor,
  selectedOpacity = 0.3,
  noteText = '',
  highlightColors,
  onColorChange,
  onOpacityChange,
  onNoteChange,
  onSave,
  onCancel,
  mode = 'create'
}: HighlightPopoverProps) {
  const [localNoteText, setLocalNoteText] = useState(noteText)
  const [localOpacity, setLocalOpacity] = useState(selectedOpacity)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    setLocalNoteText(noteText)
  }, [noteText])

  useEffect(() => {
    setLocalOpacity(selectedOpacity)
  }, [selectedOpacity])

  useEffect(() => {
    if (isOpen && textareaRef.current) {
      textareaRef.current.focus()
    }
  }, [isOpen])

  if (!isOpen) {
    return null
  }

  const handleSave = () => {
    onNoteChange(localNoteText)
    onSave()
  }

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40"
        onClick={onCancel}
      />

      {/* Popover */}
      <div
        className="absolute z-50 bg-white rounded-lg shadow-xl border border-gray-200 min-w-[280px] max-w-[400px]"
        style={{
          top: `${position.top}px`,
          left: `${position.left}px`,
          transform: 'translateY(8px)'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-4">
          {/* Selected text preview */}
          <div className="mb-3">
            <p className="text-xs text-gray-500 mb-2 font-medium">
              {mode === 'create' ? 'Selected text:' : 'Highlighted text:'}
            </p>
            <p className="text-sm text-gray-700 italic bg-gray-50 p-2 rounded line-clamp-2">
              {selectedText}
            </p>
          </div>

          {/* Color picker */}
          {mode === 'create' && (
            <>
              <div className="mb-3">
                <p className="text-xs text-gray-500 mb-2 font-medium">Choose highlight color:</p>
                <div className="flex items-center space-x-2">
                  {highlightColors.map((color) => (
                    <button
                      key={color.value}
                      type="button"
                      onClick={() => onColorChange(color.value)}
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

              {/* Opacity/Alpha slider */}
              {onOpacityChange && (
                <div className="mb-3">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-xs text-gray-500 font-medium">Opacity:</p>
                    <span className="text-xs text-gray-600">{Math.round(localOpacity * 100)}%</span>
                  </div>
                  <input
                    type="range"
                    min="0.1"
                    max="0.9"
                    step="0.05"
                    value={localOpacity}
                    onChange={(e) => {
                      const newOpacity = parseFloat(e.target.value)
                      setLocalOpacity(newOpacity)
                      onOpacityChange(newOpacity)
                    }}
                    className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
                    style={{
                      background: `linear-gradient(to right, rgba(0,0,0,0.1) 0%, rgba(0,0,0,${localOpacity}) 100%)`
                    }}
                  />
                  <div className="flex items-center justify-between mt-1">
                    <span className="text-xs text-gray-400">Light</span>
                    <span className="text-xs text-gray-400">Dark</span>
                  </div>
                </div>
              )}
            </>
          )}

          {/* Note input */}
          <div className="mb-3">
            <textarea
              ref={textareaRef}
              value={localNoteText}
              onChange={(e) => setLocalNoteText(e.target.value)}
              placeholder="Add a note (optional)..."
              rows={3}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
            />
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end space-x-2">
            <button
              onClick={onCancel}
              className="px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 rounded"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              className="px-4 py-1.5 text-sm bg-primary-600 text-white rounded hover:bg-primary-700"
            >
              {mode === 'create' ? 'Save' : 'Update'}
            </button>
          </div>
        </div>
      </div>
    </>
  )
}

