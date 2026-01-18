/**
 * API client for highlights/notes
 * 
 * Provides functions to fetch, create, update, and delete highlights.
 * Can be extended to use actual backend API or mocked for development.
 */

import type { HighlightRecord, HighlightAnchor } from './types'

export interface CreateHighlightRequest {
  documentId: string
  anchor: HighlightAnchor
  color: string
  opacity?: number
  noteText?: string
}

export interface UpdateHighlightRequest {
  color?: string
  opacity?: number
  noteText?: string
}

/**
 * Fetch highlights for a document
 */
export async function fetchHighlights(
  documentId: string,
  userId: string
): Promise<HighlightRecord[]> {
  // TODO: Replace with actual API call
  // For now, return empty array
  // Example:
  // const response = await fetch(`/api/documents/${documentId}/highlights?userId=${userId}`)
  // return response.json()

  try {
    // Try to use existing notes API if available
    const response = await fetch(`/api/lectures/${documentId}/notes`)
    if (response.ok) {
      const notes = await response.json()
      // Convert legacy Note format to HighlightRecord format
      return notes.map((note: any) => convertLegacyNoteToHighlight(note))
    }
  } catch (e) {
  }

  return []
}

/**
 * Create a new highlight
 */
export async function createHighlight(
  request: CreateHighlightRequest
): Promise<HighlightRecord> {
  // TODO: Replace with actual API call
  // Example:
  // const response = await fetch(`/api/documents/${request.documentId}/highlights`, {
  //   method: 'POST',
  //   headers: { 'Content-Type': 'application/json' },
  //   body: JSON.stringify(request)
  // })
  // return response.json()

  try {
    // Try to use existing notes API
    const response = await fetch(`/api/lectures/${request.documentId}/notes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        lectureId: request.documentId,
        courseId: request.documentId, // May need adjustment
        highlightedText: request.anchor.textQuote.exact,
        highlightColor: request.color,
        noteText: request.noteText,
        context: JSON.stringify(request.anchor) // Store anchor in context field
      })
    })

    if (response.ok) {
      const note = await response.json()
      return convertLegacyNoteToHighlight(note)
    }
  } catch (e) {
  }

  // Fallback: return mock
  return {
    id: `hl-${Date.now()}`,
    documentId: request.documentId,
    userId: 'current-user',
    anchor: request.anchor,
    color: request.color,
    opacity: request.opacity,
    noteText: request.noteText,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }
}

/**
 * Update an existing highlight
 */
export async function updateHighlight(
  highlightId: string,
  request: UpdateHighlightRequest
): Promise<HighlightRecord> {
  // TODO: Replace with actual API call
  // Example:
  // const response = await fetch(`/api/highlights/${highlightId}`, {
  //   method: 'PUT',
  //   headers: { 'Content-Type': 'application/json' },
  //   body: JSON.stringify(request)
  // })
  // return response.json()

  try {
    // Try to use existing notes API
    const response = await fetch(`/api/notes/${highlightId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        highlightColor: request.color,
        noteText: request.noteText
      })
    })

    if (response.ok) {
      const note = await response.json()
      return convertLegacyNoteToHighlight(note)
    }
  } catch (e) {
  }

  throw new Error('Failed to update highlight')
}

/**
 * Delete a highlight
 */
export async function deleteHighlight(highlightId: string): Promise<void> {
  // TODO: Replace with actual API call
  // Example:
  // await fetch(`/api/highlights/${highlightId}`, { method: 'DELETE' })

  try {
    const response = await fetch(`/api/notes/${highlightId}`, {
      method: 'DELETE'
    })

    if (!response.ok) {
      throw new Error('Failed to delete highlight')
    }
  } catch (e) {
    throw e
  }
}

/**
 * Convert legacy Note format to HighlightRecord format
 */
function convertLegacyNoteToHighlight(note: any): HighlightRecord {
  let anchor: HighlightAnchor

  // Try to parse anchor from context field
  if (note.context) {
    try {
      const parsed = JSON.parse(note.context)
      if (parsed.textQuote) {
        anchor = parsed
      } else {
        // Fallback: create anchor from highlightedText
        anchor = createAnchorFromText(note.highlightedText || '')
      }
    } catch (e) {
      // Context is not JSON, create anchor from text
      anchor = createAnchorFromText(note.highlightedText || '')
    }
  } else {
    // No context, create anchor from highlightedText
    anchor = createAnchorFromText(note.highlightedText || '')
  }

  return {
    id: note.id,
    documentId: note.lectureId || note.documentId,
    userId: note.studentId || note.userId,
    anchor,
    color: note.highlightColor || note.color || '#FFEB3B',
    noteText: note.noteText,
    createdAt: note.createdAt,
    updatedAt: note.updatedAt,
    // Legacy fields for backward compatibility
    highlightedText: note.highlightedText,
    highlightColor: note.highlightColor,
    context: note.context
  }
}

/**
 * Create a basic anchor from text (fallback for legacy notes)
 */
function createAnchorFromText(text: string): HighlightAnchor {
  return {
    textQuote: {
      type: 'TextQuoteSelector',
      exact: text,
      prefix: '',
      suffix: ''
    }
  }
}

