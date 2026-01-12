/**
 * Unit tests for anchor resolution functionality
 */

import { describe, it, expect, beforeEach } from '@jest/globals'
import { resolveHighlight } from '../anchorResolve'
import { buildTextIndex } from '../textIndex'
import type { HighlightRecord } from '../types'

describe('anchorResolve', () => {
  let container: HTMLDivElement

  beforeEach(() => {
    container = document.createElement('div')
    document.body.appendChild(container)
  })

  afterEach(() => {
    document.body.removeChild(container)
  })

  describe('resolveHighlight', () => {
    it('should resolve highlight with matching content hash', () => {
      container.textContent = 'Hello world'
      const index = buildTextIndex(container)

      const record: HighlightRecord = {
        id: 'test-1',
        documentId: 'doc-1',
        userId: 'user-1',
        anchor: {
          textPosition: {
            type: 'TextPositionSelector',
            start: 0,
            end: 5
          },
          textQuote: {
            type: 'TextQuoteSelector',
            exact: 'Hello',
            prefix: '',
            suffix: ' '
          },
          contentHashAtCreate: index.contentHash
        },
        color: '#FFEB3B',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }

      const resolved = resolveHighlight(record, index, container)
      expect(resolved.isOrphaned).toBe(false)
      expect(resolved.confidence).toBe('high')
      expect(resolved.range).not.toBeNull()
    })

    it('should resolve highlight using text quote when hash mismatch', () => {
      container.textContent = 'Hello world'
      const index = buildTextIndex(container)

      const record: HighlightRecord = {
        id: 'test-2',
        documentId: 'doc-1',
        userId: 'user-1',
        anchor: {
          textQuote: {
            type: 'TextQuoteSelector',
            exact: 'Hello',
            prefix: '',
            suffix: ' '
          },
          contentHashAtCreate: 'different-hash'
        },
        color: '#FFEB3B',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }

      const resolved = resolveHighlight(record, index, container)
      expect(resolved.isOrphaned).toBe(false)
      expect(resolved.range).not.toBeNull()
    })

    it('should mark as orphaned when text not found', () => {
      container.textContent = 'Hello world'
      const index = buildTextIndex(container)

      const record: HighlightRecord = {
        id: 'test-3',
        documentId: 'doc-1',
        userId: 'user-1',
        anchor: {
          textQuote: {
            type: 'TextQuoteSelector',
            exact: 'Not found',
            prefix: '',
            suffix: ''
          }
        },
        color: '#FFEB3B',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }

      const resolved = resolveHighlight(record, index, container)
      expect(resolved.isOrphaned).toBe(true)
      expect(resolved.range).toBeNull()
    })
  })
})

