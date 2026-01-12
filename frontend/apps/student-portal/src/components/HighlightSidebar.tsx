/**
 * HighlightSidebar: Sidebar component for listing highlights and orphaned highlights
 */

'use client'

import React, { useState } from 'react'
import type { HighlightRecord, ResolvedHighlight } from '../highlights/types'

export interface HighlightSidebarProps {
  highlights: ResolvedHighlight[]
  onHighlightClick: (highlightId: string) => void
  onDeleteHighlight: (highlightId: string) => void
  onEditHighlight?: (highlightId: string) => void
  isOpen: boolean
  onClose: () => void
  className?: string
}

export function HighlightSidebar({
  highlights,
  onHighlightClick,
  onDeleteHighlight,
  onEditHighlight,
  isOpen,
  onClose,
  className = ''
}: HighlightSidebarProps) {
  const [filter, setFilter] = useState<'all' | 'orphaned' | 'with-notes'>('all')

  const validHighlights = highlights.filter(h => !h.isOrphaned)
  const orphanedHighlights = highlights.filter(h => h.isOrphaned)
  const highlightsWithNotes = highlights.filter(h => h.record.noteText)

  const getFilteredHighlights = () => {
    switch (filter) {
      case 'orphaned':
        return orphanedHighlights
      case 'with-notes':
        return highlightsWithNotes
      default:
        return validHighlights
    }
  }

  const filteredHighlights = getFilteredHighlights()

  if (!isOpen) {
    return null
  }

  return (
    <div className={`fixed right-0 top-0 h-full w-80 bg-white shadow-xl border-l border-gray-200 z-50 flex flex-col ${className}`}>
      {/* Header */}
      <div className="p-4 border-b border-gray-200">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg font-semibold text-gray-900">Highlights</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
            aria-label="Close sidebar"
          >
            ✕
          </button>
        </div>

        {/* Filter tabs */}
        <div className="flex space-x-2">
          <button
            onClick={() => setFilter('all')}
            className={`px-2 py-1 text-xs rounded ${
              filter === 'all'
                ? 'bg-primary-100 text-primary-700'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            All ({validHighlights.length})
          </button>
          <button
            onClick={() => setFilter('with-notes')}
            className={`px-2 py-1 text-xs rounded ${
              filter === 'with-notes'
                ? 'bg-primary-100 text-primary-700'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            With Notes ({highlightsWithNotes.length})
          </button>
          {orphanedHighlights.length > 0 && (
            <button
              onClick={() => setFilter('orphaned')}
              className={`px-2 py-1 text-xs rounded ${
                filter === 'orphaned'
                  ? 'bg-yellow-100 text-yellow-700'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              Orphaned ({orphanedHighlights.length})
            </button>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {filteredHighlights.length === 0 ? (
          <div className="text-center text-gray-400 mt-8">
            <p>No highlights found</p>
          </div>
        ) : (
          <div className="space-y-3">
            {filteredHighlights.map(({ record, isOrphaned, confidence }) => (
              <div
                key={record.id}
                className={`p-3 rounded-lg border ${
                  isOrphaned
                    ? 'border-yellow-300 bg-yellow-50'
                    : 'border-gray-200 bg-white hover:border-gray-300'
                }`}
              >
                {/* Highlighted text */}
                <div
                  className="mb-2 cursor-pointer"
                  onClick={() => !isOrphaned && onHighlightClick(record.id)}
                >
                  <div
                    className="inline-block px-2 py-1 rounded text-sm"
                    style={{
                      backgroundColor: (() => {
                        // Convert hex to rgba for better text readability
                        const hex = record.color
                        const r = parseInt(hex.slice(1, 3), 16)
                        const g = parseInt(hex.slice(3, 5), 16)
                        const b = parseInt(hex.slice(5, 7), 16)
                        return `rgba(${r}, ${g}, ${b}, 0.3)`
                      })()
                    }}
                  >
                    {record.anchor.textQuote.exact.substring(0, 100)}
                    {record.anchor.textQuote.exact.length > 100 ? '...' : ''}
                  </div>
                </div>

                {/* Note text */}
                {record.noteText && (
                  <div className="mb-2 text-sm text-gray-700 bg-gray-50 p-2 rounded">
                    {record.noteText}
                  </div>
                )}

                {/* Metadata */}
                <div className="flex items-center justify-between text-xs text-gray-500">
                  <span>
                    {new Date(record.createdAt).toLocaleDateString()}
                    {isOrphaned && (
                      <span className="ml-2 text-yellow-600">(Orphaned)</span>
                    )}
                    {confidence === 'low' && !isOrphaned && (
                      <span className="ml-2 text-orange-600">(Low confidence)</span>
                    )}
                  </span>
                </div>

                {/* Actions */}
                <div className="flex items-center space-x-2 mt-2">
                  {onEditHighlight && !isOrphaned && (
                    <button
                      onClick={() => onEditHighlight(record.id)}
                      className="text-xs text-primary-600 hover:text-primary-700"
                    >
                      Edit
                    </button>
                  )}
                  <button
                    onClick={() => onDeleteHighlight(record.id)}
                    className="text-xs text-red-600 hover:text-red-700"
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

