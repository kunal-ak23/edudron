/**
 * Unit tests for text indexing functionality
 */

import { describe, it, expect, beforeEach } from '@jest/globals'
import { buildTextIndex, rangeToOffsets, offsetsToRange, extractTextQuote } from '../textIndex'

describe('textIndex', () => {
  let container: HTMLDivElement

  beforeEach(() => {
    container = document.createElement('div')
    document.body.appendChild(container)
  })

  afterEach(() => {
    document.body.removeChild(container)
  })

  describe('buildTextIndex', () => {
    it('should build index from simple text', () => {
      container.textContent = 'Hello world'
      const index = buildTextIndex(container)

      expect(index.canonicalText).toBe('Hello world')
      expect(index.mappings).toHaveLength(1)
      expect(index.mappings[0].start).toBe(0)
      expect(index.mappings[0].end).toBe(11)
    })

    it('should handle multiple text nodes', () => {
      container.innerHTML = '<p>First</p><p>Second</p>'
      const index = buildTextIndex(container)

      expect(index.canonicalText).toBe('FirstSecond')
      expect(index.mappings).toHaveLength(2)
    })

    it('should normalize NBSP to space', () => {
      const textNode = document.createTextNode('Hello\u00A0world')
      container.appendChild(textNode)
      const index = buildTextIndex(container)

      expect(index.canonicalText).toContain('Hello world')
    })

    it('should exclude script and style elements', () => {
      container.innerHTML = '<p>Visible</p><script>hidden</script><style>hidden</style>'
      const index = buildTextIndex(container)

      expect(index.canonicalText).toBe('Visible')
      expect(index.mappings).toHaveLength(1)
    })
  })

  describe('rangeToOffsets', () => {
    it('should convert range to offsets', () => {
      container.textContent = 'Hello world'
      const index = buildTextIndex(container)

      const range = document.createRange()
      range.setStart(container.firstChild!, 0)
      range.setEnd(container.firstChild!, 5)

      const offsets = rangeToOffsets(range, index)
      expect(offsets).toEqual({ start: 0, end: 5 })
    })

    it('should handle multi-node ranges', () => {
      container.innerHTML = '<p>First</p><p>Second</p>'
      const index = buildTextIndex(container)

      const range = document.createRange()
      range.setStart(container.firstChild!.firstChild!, 0)
      range.setEnd(container.lastChild!.firstChild!, 3)

      const offsets = rangeToOffsets(range, index)
      expect(offsets).not.toBeNull()
      expect(offsets!.start).toBe(0)
      expect(offsets!.end).toBeGreaterThan(5)
    })
  })

  describe('offsetsToRange', () => {
    it('should convert offsets to range', () => {
      container.textContent = 'Hello world'
      const index = buildTextIndex(container)

      const range = offsetsToRange(0, 5, index)
      expect(range).not.toBeNull()
      expect(range!.toString()).toBe('Hello')
    })
  })

  describe('extractTextQuote', () => {
    it('should extract text quote with context', () => {
      const text = 'This is a long text with some content to highlight'
      const quote = extractTextQuote(text, 10, 20, 5)

      expect(quote.exact).toBe('long text ')
      expect(quote.prefix.length).toBeGreaterThan(0)
      expect(quote.suffix.length).toBeGreaterThan(0)
    })
  })
})

