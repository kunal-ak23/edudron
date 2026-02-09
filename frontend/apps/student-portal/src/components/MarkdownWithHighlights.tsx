'use client'

import React, { useState, useEffect, useRef, useCallback } from 'react'
import { unstable_batchedUpdates } from 'react-dom'
import { MarkdownRenderer } from './MarkdownRenderer'
import type { Note } from '@kunal-ak23/edudron-shared-utils'

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
  const isApplyingHighlightsRef = useRef(false)
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
  // Returns either a single node match or a multi-node range
  const findMarkdownTextInRendered = (
    markdownText: string, 
    container: Node
  ): { node: Text; start: number; end: number } | { startNode: Text; startOffset: number; endNode: Text; endOffset: number } | null => {
    if (!markdownText) return null

    // Remove markdown syntax to get plain text for searching
    // This handles: **bold**, *italic*, `code`, # headers, etc.
    // Process in order to avoid partial matches
    let plainText = markdownText
    
    // First, handle nested/overlapping markdown (like **bold*italic**)
    // Remove bold first (longer pattern) - handle multiple asterisks
    plainText = plainText.replace(/\*\*([^*]+)\*\*/g, '$1')
    // Then remove italic (single asterisk that's not part of **)
    // Use a pattern that matches *text* but not **text**
    plainText = plainText.replace(/\*([^*\n]+?)\*/g, '$1')
    // Remove code blocks
    plainText = plainText.replace(/`([^`]+)`/g, '$1')
    // Remove headers
    plainText = plainText.replace(/#+\s+/g, '')
    // Remove links but keep the text
    plainText = plainText.replace(/\[([^\]]+)\]\([^\)]+\)/g, '$1')
    // Clean up any remaining markdown chars that are standalone
    plainText = plainText.replace(/\s+[*_`#\[\]()]\s+/g, ' ')
    // Remove markdown chars from start/end
    plainText = plainText.replace(/^[*_`#\[\]()\s]+/, '')
    plainText = plainText.replace(/[*_`#\[\]()\s]+$/, '')
    
    plainText = plainText.trim()
    const normalizedPlain = normalizeText(plainText)
    if (normalizedPlain.length === 0) return null
    
    // Get all text nodes and combine them into a searchable string
    const textNodes = getAllTextNodes(container)
    
    // Build a combined text string with node references for position mapping
    interface TextNodeInfo {
      node: Text
      start: number
      end: number
      text: string
    }
    
    const nodeInfos: TextNodeInfo[] = []
    let combinedText = ''
    
    textNodes.forEach(node => {
      const nodeText = node.textContent || ''
      const start = combinedText.length
      const end = start + nodeText.length
      nodeInfos.push({ node, start, end, text: nodeText })
      combinedText += nodeText
    })
    
    // Strategy 1: Try exact match of plain text in combined text
    // CRITICAL: Always try to match the FULL text first, not a substring
    // If the stored text is long, we must match the entire length
    const exactIndex = combinedText.indexOf(plainText)
    
    if (exactIndex !== -1) {
      // Verify this is a complete match by checking boundaries
      // For long text (>50 chars), require word boundaries to avoid partial matches
      const isLongText = plainText.length > 50
      const isStartBoundary = exactIndex === 0 || /\s/.test(combinedText[exactIndex - 1])
      const isEndBoundary = exactIndex + plainText.length >= combinedText.length || 
                           /\s/.test(combinedText[exactIndex + plainText.length])
      
      // For long text, we MUST have word boundaries to ensure it's the full match
      if (!isLongText || (isStartBoundary && isEndBoundary)) {
        const endIndex = exactIndex + plainText.length
        
        // Verify the match is complete by checking if there are other occurrences
        // If there are multiple occurrences, prefer ones with better context
        const nextOccurrence = combinedText.indexOf(plainText, exactIndex + 1)
        const prevOccurrence = combinedText.lastIndexOf(plainText, exactIndex - 1)
        
        // If there are multiple occurrences, we need to be more careful
        // But for now, if we found a match with proper boundaries, use it
        if (nextOccurrence === -1 || (isStartBoundary && isEndBoundary)) {
          // Find start and end nodes
          let startNode: TextNodeInfo | null = null
          let endNode: TextNodeInfo | null = null
          let startOffset = 0
          let endOffset = 0
          
          for (const info of nodeInfos) {
            // Find the node containing the start position
            if (!startNode && exactIndex >= info.start && exactIndex < info.end) {
              startNode = info
              startOffset = exactIndex - info.start
            }
            
            // Find the node containing the end position
            if (endIndex > info.start && endIndex <= info.end) {
              endNode = info
              endOffset = endIndex - info.start
              break
            }
          }
          
          if (startNode && endNode) {
            // If it's a single node, return single node format
            if (startNode === endNode) {
              if (endOffset > startOffset) {
                // Verify we're matching a reasonable portion of the text
                const matchedText = startNode.text.substring(startOffset, endOffset)
                if (matchedText.length >= Math.min(plainText.length * 0.8, plainText.length - 20)) {
                  return { node: startNode.node, start: startOffset, end: endOffset }
                }
              }
            } else {
              // Multi-node range - calculate actual matched length
              let matchedLength = startNode.text.length - startOffset // Remaining text in start node
              const startIdx = nodeInfos.indexOf(startNode)
              const endIdx = nodeInfos.indexOf(endNode)
              
              // Add full text of nodes between start and end
              for (let i = startIdx + 1; i < endIdx; i++) {
                matchedLength += nodeInfos[i].text.length
              }
              
              // Add text from start of end node
              matchedLength += endOffset
              
              // Verify we're matching a reasonable portion (allow 20 char difference for whitespace)
              if (endOffset > 0 && Math.abs(matchedLength - plainText.length) <= 20) {
                return {
                  startNode: startNode.node,
                  startOffset: startOffset,
                  endNode: endNode.node,
                  endOffset: endOffset
                }
              }
            }
          }
        }
      }
    }
    
    // If exact match failed, try to find the longest possible substring match
    // This handles cases where whitespace normalization caused slight differences
    // But prioritize longer matches
    if (plainText.length > 20) {
      // Try progressively shorter suffixes to find the longest match
      for (let len = plainText.length; len >= Math.max(20, plainText.length * 0.7); len -= 10) {
        const substring = plainText.substring(0, len)
        const subIndex = combinedText.indexOf(substring)
        
        if (subIndex !== -1) {
          const isStartBoundary = subIndex === 0 || /\s/.test(combinedText[subIndex - 1])
          const isEndBoundary = subIndex + substring.length >= combinedText.length || 
                               /\s/.test(combinedText[subIndex + substring.length])
          
          // Only use substring match if it has proper boundaries
          if (isStartBoundary && isEndBoundary) {
            const endIndex = subIndex + substring.length
            
            let startNode: TextNodeInfo | null = null
            let endNode: TextNodeInfo | null = null
            let startOffset = 0
            let endOffset = 0
            
            for (const info of nodeInfos) {
              if (!startNode && subIndex >= info.start && subIndex < info.end) {
                startNode = info
                startOffset = subIndex - info.start
              }
              
              if (endIndex > info.start && endIndex <= info.end) {
                endNode = info
                endOffset = endIndex - info.start
                break
              }
            }
            
            if (startNode && endNode) {
              if (startNode === endNode) {
                if (endOffset > startOffset) {
                  return { node: startNode.node, start: startOffset, end: endOffset }
                }
              } else {
                if (endOffset > 0) {
                  return {
                    startNode: startNode.node,
                    startOffset: startOffset,
                    endNode: endNode.node,
                    endOffset: endOffset
                  }
                }
              }
            }
          }
        }
      }
    }

    // Strategy 2: Normalized matching in combined text
    // This handles cases where whitespace differs between stored and rendered text
    const normalizedCombined = normalizeText(combinedText)
    if (normalizedCombined.includes(normalizedPlain)) {
      const normalizedIndex = normalizedCombined.indexOf(normalizedPlain)
      const normalizedEndIndex = normalizedIndex + normalizedPlain.length
      
      // Map normalized start position back to actual position in combined text
      let actualStartPos = 0
      let normalizedPos = 0
      
      while (normalizedPos < normalizedIndex && actualStartPos < combinedText.length) {
        const char = combinedText[actualStartPos]
        if (/\s/.test(char)) {
          actualStartPos++
          while (actualStartPos < combinedText.length && /\s/.test(combinedText[actualStartPos])) {
            actualStartPos++
          }
        } else {
          normalizedPos++
          actualStartPos++
        }
      }
      
      // Map normalized end position back to actual position
      let actualEndPos = actualStartPos
      normalizedPos = normalizedIndex
      
      while (normalizedPos < normalizedEndIndex && actualEndPos < combinedText.length) {
        const char = combinedText[actualEndPos]
        if (/\s/.test(char)) {
          actualEndPos++
          while (actualEndPos < combinedText.length && /\s/.test(combinedText[actualEndPos])) {
            actualEndPos++
          }
        } else {
          normalizedPos++
          actualEndPos++
        }
      }
      
      // Find start and end nodes
      let startNode: TextNodeInfo | null = null
      let endNode: TextNodeInfo | null = null
      let startOffset = 0
      let endOffset = 0
      
      for (const info of nodeInfos) {
        // Find the node containing the start position
        if (!startNode && actualStartPos >= info.start && actualStartPos < info.end) {
          startNode = info
          startOffset = actualStartPos - info.start
        }
        
        // Find the node containing the end position
        if (actualEndPos > info.start && actualEndPos <= info.end) {
          endNode = info
          endOffset = actualEndPos - info.start
          break
        }
      }
      
      if (startNode && endNode) {
        // Verify the match length is reasonable
        let matchedLength = 0
        if (startNode === endNode) {
          matchedLength = endOffset - startOffset
        } else {
          matchedLength = startNode.text.length - startOffset
          const startIdx = nodeInfos.indexOf(startNode)
          const endIdx = nodeInfos.indexOf(endNode)
          for (let i = startIdx + 1; i < endIdx; i++) {
            matchedLength += nodeInfos[i].text.length
          }
          matchedLength += endOffset
        }
        
        // Allow up to 20 char difference for whitespace normalization
        if (Math.abs(matchedLength - plainText.length) <= 20) {
          // If it's a single node, return single node format
          if (startNode === endNode) {
            if (endOffset > startOffset) {
              return { node: startNode.node, start: startOffset, end: endOffset }
            }
          } else {
            // Multi-node range
            if (endOffset > 0) {
              return {
                startNode: startNode.node,
                startOffset: startOffset,
                endNode: endNode.node,
                endOffset: endOffset
              }
            }
          }
        }
      }
    }

    // Strategy 3: Word-by-word matching (for longer texts or when text spans nodes)
    const words = normalizedPlain.split(' ').filter(w => w.length > 1)
    if (words.length >= 2) {
      // Try different phrase lengths
      for (let phraseLength = Math.min(5, words.length); phraseLength >= 2; phraseLength--) {
        const searchPhrase = words.slice(0, phraseLength).join(' ')
        
        if (normalizedCombined.includes(searchPhrase)) {
          const normalizedIndex = normalizedCombined.indexOf(searchPhrase)
          
          // Map back to actual position
          let actualPos = 0
          let normalizedPos = 0
          
          while (normalizedPos < normalizedIndex && actualPos < combinedText.length) {
            const char = combinedText[actualPos]
            if (/\s/.test(char)) {
              actualPos++
              while (actualPos < combinedText.length && /\s/.test(combinedText[actualPos])) {
                actualPos++
              }
            } else {
              normalizedPos++
              actualPos++
            }
          }
          
          // Find start and end positions in combined text
          const endPos = actualPos + plainText.length
          
          // Find start and end nodes
          let startNode: TextNodeInfo | null = null
          let endNode: TextNodeInfo | null = null
          let startOffset = 0
          let endOffset = 0
          
          for (const info of nodeInfos) {
            // Find the node containing the start position
            if (!startNode && actualPos >= info.start && actualPos < info.end) {
              startNode = info
              startOffset = actualPos - info.start
            }
            
            // Find the node containing the end position
            if (endPos > info.start && endPos <= info.end) {
              endNode = info
              endOffset = endPos - info.start
              break
            }
          }
          
          if (startNode && endNode) {
            // If it's a single node, return single node format
            if (startNode === endNode) {
              if (endOffset > startOffset) {
                return { node: startNode.node, start: startOffset, end: endOffset }
              }
            } else {
              // Multi-node range
              if (endOffset > 0) {
                return {
                  startNode: startNode.node,
                  startOffset: startOffset,
                  endNode: endNode.node,
                  endOffset: endOffset
                }
              }
            }
          }
        }
      }
    }

    // Strategy 4: Try to find text that might span multiple nodes
    // Search for the first few words in each node and try to build a range
    if (words.length >= 2) {
      const firstWords = words.slice(0, Math.min(3, words.length)).join(' ')
      const normalizedFirstWords = normalizeText(firstWords)
      
      // Try to find the first words in any node
      for (let i = 0; i < nodeInfos.length; i++) {
        const info = nodeInfos[i]
        const normalizedNodeText = normalizeText(info.text)
        
        if (normalizedNodeText.includes(normalizedFirstWords)) {
          const normalizedIndex = normalizedNodeText.indexOf(normalizedFirstWords)
          
          // Map back to actual position in this node
          let nodeStart = 0
          let normalizedPos = 0
          let actualPos = 0
          
          while (normalizedPos < normalizedIndex && actualPos < info.text.length) {
            const char = info.text[actualPos]
            if (/\s/.test(char)) {
              actualPos++
              while (actualPos < info.text.length && /\s/.test(info.text[actualPos])) {
                actualPos++
              }
            } else {
              normalizedPos++
              actualPos++
            }
          }
          
          nodeStart = actualPos
          
          // Try to find where the text ends, possibly spanning multiple nodes
          let remainingText = normalizedPlain.substring(normalizedFirstWords.length).trim()
          let currentNode = i
          let currentOffset = nodeStart
          let endNode = i
          let endOffset = nodeStart
          
          // Start from after the first words in the first node
          let remainingInNode = normalizeText(info.text.substring(nodeStart))
          let remainingNormalized = remainingInNode
          
          // Try to match the remaining text across nodes
          while (remainingText.length > 0 && currentNode < nodeInfos.length) {
            const currentInfo = nodeInfos[currentNode]
            const nodeText = currentNode === i 
              ? currentInfo.text.substring(nodeStart)
              : currentInfo.text
            const normalizedNodeText = normalizeText(nodeText)
            
            // Check if remaining text starts with this node's text
            if (normalizedNodeText.length > 0 && remainingText.startsWith(normalizedNodeText)) {
              // This node is fully consumed
              remainingText = remainingText.substring(normalizedNodeText.length).trim()
              endNode = currentNode
              endOffset = currentNode === i ? currentInfo.text.length : currentInfo.text.length
              currentNode++
            } else if (remainingText.startsWith(normalizedNodeText.substring(0, Math.min(remainingText.length, normalizedNodeText.length)))) {
              // Partial match - text ends in this node
              const matchedLength = Math.min(remainingText.length, normalizedNodeText.length)
              endNode = currentNode
              endOffset = currentNode === i 
                ? nodeStart + matchedLength
                : matchedLength
              remainingText = ''
              break
            } else {
              // No match in this node, but we might have found enough
              break
            }
          }
          
          if (endNode === i) {
            // All text is in one node
            const nodeEnd = Math.min(nodeStart + plainText.length, info.text.length)
            return { node: info.node, start: nodeStart, end: nodeEnd }
          } else {
            // Text spans multiple nodes - return multi-node range
            return {
              startNode: info.node,
              startOffset: nodeStart,
              endNode: nodeInfos[endNode].node,
              endOffset: endOffset
            }
          }
        }
      }
    }

    // Strategy 5: Last resort - try to find any significant words from the text
    // This is useful when the stored text has markdown that doesn't match exactly
    if (words.length >= 2) {
      // Try to find the first significant word
      const firstWord = words[0]
      if (firstWord.length >= 3) {
        for (let i = 0; i < nodeInfos.length; i++) {
          const info = nodeInfos[i]
          const normalizedNodeText = normalizeText(info.text)
          
          if (normalizedNodeText.includes(firstWord)) {
            const normalizedIndex = normalizedNodeText.indexOf(firstWord)
            
            // Map back to actual position
            let nodeStart = 0
            let normalizedPos = 0
            let actualPos = 0
            
            while (normalizedPos < normalizedIndex && actualPos < info.text.length) {
              const char = info.text[actualPos]
              if (/\s/.test(char)) {
                actualPos++
                while (actualPos < info.text.length && /\s/.test(info.text[actualPos])) {
                  actualPos++
                }
              } else {
                normalizedPos++
                actualPos++
              }
            }
            
            nodeStart = actualPos
            
            // Try to extend to find more matching words
            let matchedWords = 1
            let currentNode = i
            let currentOffset = nodeStart
            let endNode = i
            let endOffset = nodeStart + firstWord.length
            
            // Look for subsequent words in the same or next nodes
            for (let w = 1; w < Math.min(words.length, 10); w++) {
              const word = words[w]
              if (word.length < 2) continue
              
              let found = false
              // Check current node first
              if (currentNode < nodeInfos.length) {
                const currentInfo = nodeInfos[currentNode]
                const remainingText = currentNode === i 
                  ? normalizeText(currentInfo.text.substring(currentOffset))
                  : normalizeText(currentInfo.text)
                
                if (remainingText.startsWith(word) || remainingText.includes(' ' + word + ' ')) {
                  const wordIndex = remainingText.indexOf(word)
                  if (wordIndex === 0 || remainingText[wordIndex - 1] === ' ') {
                    matchedWords++
                    endNode = currentNode
                    endOffset = currentNode === i 
                      ? currentOffset + wordIndex + word.length
                      : wordIndex + word.length
                    currentOffset = endOffset
                    found = true
                  }
                }
              }
              
              // If not found in current node, check next nodes
              if (!found && currentNode + 1 < nodeInfos.length) {
                currentNode++
                const nextInfo = nodeInfos[currentNode]
                const normalizedNextText = normalizeText(nextInfo.text)
                
                if (normalizedNextText.startsWith(word) || normalizedNextText.includes(' ' + word + ' ')) {
                  const wordIndex = normalizedNextText.indexOf(word)
                  if (wordIndex === 0 || normalizedNextText[wordIndex - 1] === ' ') {
                    matchedWords++
                    endNode = currentNode
                    endOffset = wordIndex + word.length
                    currentOffset = endOffset
                    found = true
                  }
                }
              }
              
              // If we can't find the next word, stop
              if (!found) break
            }
            
            // If we found at least 2 words, create a highlight
            if (matchedWords >= 2) {
              if (endNode === i) {
                return { node: info.node, start: nodeStart, end: endOffset }
              } else {
                return {
                  startNode: info.node,
                  startOffset: nodeStart,
                  endNode: nodeInfos[endNode].node,
                  endOffset: endOffset
                }
              }
            }
          }
        }
      }
    }

    return null
  }

  // Apply highlights to rendered markdown
  useEffect(() => {
    if (!containerRef.current) return
    if (isApplyingHighlightsRef.current) return // Prevent concurrent application

    // Use requestIdleCallback if available, otherwise use setTimeout
    const scheduleWork = (callback: () => void) => {
      if ('requestIdleCallback' in window) {
        (window as any).requestIdleCallback(callback, { timeout: 1000 })
      } else {
        setTimeout(callback, 100)
      }
    }

    const applyHighlights = () => {
      if (!containerRef.current || isApplyingHighlightsRef.current) return
      
      isApplyingHighlightsRef.current = true

      try {
        // Remove existing highlights first - properly unwrap them
        const marks = containerRef.current.querySelectorAll('.highlight-mark')
        // Convert to array and process in reverse to avoid issues
        const marksArray = Array.from(marks).reverse()
        marksArray.forEach(mark => {
          const parent = mark.parentNode
          if (parent && parent.contains(mark)) {
            try {
              // Replace the mark with its text content
              const textNode = document.createTextNode(mark.textContent || '')
              parent.replaceChild(textNode, mark)
              // Normalize to merge adjacent text nodes
              parent.normalize()
            } catch (e) {
              // Node may have already been removed
            }
          }
        })

        if (notes.length === 0) {
          isApplyingHighlightsRef.current = false
          return
        }

        // Wait for markdown to fully render and React to be idle
        // Use multiple delays to ensure React has finished all reconciliation
        const timeoutId = setTimeout(() => {
          // Use requestIdleCallback to wait for React to be completely idle
          const idleCallback = (deadline?: { timeRemaining: () => number }) => {
            if (!containerRef.current) {
              isApplyingHighlightsRef.current = false
              return
            }

            // Check if we have time, or if this is a forced callback
            if (deadline && deadline.timeRemaining() < 1) {
              // Not enough time, schedule for later
              if ('requestIdleCallback' in window) {
                (window as any).requestIdleCallback(idleCallback, { timeout: 500 })
              } else {
                setTimeout(idleCallback, 50)
              }
              return
            }

            notes.forEach((note) => {
            if (!note.highlightedText) {
              return
            }

        // Find the markdown text in the rendered content
        const match = findMarkdownTextInRendered(note.highlightedText, containerRef.current!)
        
        if (!match) {
          return
        }
        
        if (match) {
          const range = document.createRange()
          
          // Handle both single-node and multi-node matches
          if ('node' in match) {
            // Single node match
            // Validate that end > start
            if (match.end <= match.start) {
              return
            }
            
            try {
              range.setStart(match.node, match.start)
              range.setEnd(match.node, match.end)
              
              // Verify the range contains the expected text
              const rangeText = range.toString()
              const normalizedRangeText = normalizeText(rangeText)
              const normalizedStoredText = normalizeText(note.highlightedText)
              
              // Verify the match is at least 80% of the stored text length
              // This handles cases where whitespace differs
              const minMatchLength = Math.max(normalizedStoredText.length * 0.8, normalizedStoredText.length - 50)
              if (normalizedRangeText.length < minMatchLength) {
                return // Skip this highlight - it's not a good match
              }
            } catch (e) {
              return
            }
          } else {
            // Multi-node match
            try {
              range.setStart(match.startNode, match.startOffset)
              range.setEnd(match.endNode, match.endOffset)
              
              // Verify the range contains the expected text
              const rangeText = range.toString()
              const normalizedRangeText = normalizeText(rangeText)
              const normalizedStoredText = normalizeText(note.highlightedText)
              
              // Verify the match is at least 80% of the stored text length
              const minMatchLength = Math.max(normalizedStoredText.length * 0.8, normalizedStoredText.length - 50)
              if (normalizedRangeText.length < minMatchLength) {
                return // Skip this highlight - it's not a good match
              }
            } catch (e) {
              return
            }
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

          // Validate range is still valid before manipulating
          if (!range.startContainer.parentNode || !range.endContainer.parentNode) {
            return
          }

          // Check if range is still valid
          try {
            range.toString() // This will throw if range is invalid
          } catch (e) {
            return
          }

          try {
            // For single-node ranges, use surroundContents
            if (range.startContainer === range.endContainer && range.startContainer.nodeType === Node.TEXT_NODE) {
              // Single text node - safe to use surroundContents
              range.surroundContents(mark)
            } else {
              // Multi-node range - use a safer approach that doesn't extract nodes
              // This avoids breaking React's DOM reconciliation
              const safeRange = range.cloneRange()
              
              // Get all text nodes in the range
              const textNodes: Text[] = []
              const walker = document.createTreeWalker(
                safeRange.commonAncestorContainer,
                NodeFilter.SHOW_TEXT,
                {
                  acceptNode: (node) => {
                    try {
                      if (safeRange.intersectsNode(node)) {
                        return NodeFilter.FILTER_ACCEPT
                      }
                    } catch (e) {
                      // Node might be in different document
                    }
                    return NodeFilter.FILTER_REJECT
                  }
                }
              )
              
              let node: Node | null
              while ((node = walker.nextNode())) {
                if (node.nodeType === Node.TEXT_NODE) {
                  textNodes.push(node as Text)
                }
              }
              
              if (textNodes.length === 0) {
                return
              }
              
              // Collect all replacements first, then apply them
              // This prevents issues with DOM structure changing during iteration
              const replacements: Array<{ node: Text; fragment: DocumentFragment; mark: HTMLElement }> = []
              
              for (let i = textNodes.length - 1; i >= 0; i--) {
                const textNode = textNodes[i]
                if (!textNode.parentNode || !textNode.parentNode.contains(textNode)) {
                  continue // Node already removed
                }
                
                // Calculate which portion of this text node is in the range
                let startOffset = 0
                let endOffset = textNode.textContent?.length || 0
                
                // Create a range for this text node to compare with safeRange
                const nodeRange = document.createRange()
                try {
                  nodeRange.selectNodeContents(textNode)
                  
                  // Check if this node intersects with the range
                  const startCompare = safeRange.compareBoundaryPoints(Range.START_TO_START, nodeRange)
                  const endCompare = safeRange.compareBoundaryPoints(Range.END_TO_END, nodeRange)
                  
                  // If range starts after this node ends, or ends before this node starts, skip
                  if (startCompare > 0 || endCompare < 0) {
                    continue
                  }
                  
                  // Calculate start offset
                  if (safeRange.startContainer === textNode) {
                    startOffset = safeRange.startOffset
                  } else if (startCompare <= 0) {
                    // Range starts before or at this node
                    startOffset = 0
                  } else {
                    // Range starts within this node - calculate offset
                    const tempRange = safeRange.cloneRange()
                    tempRange.setStart(nodeRange.startContainer, nodeRange.startOffset)
                    startOffset = tempRange.toString().length
                  }
                  
                  // Calculate end offset
                  if (safeRange.endContainer === textNode) {
                    endOffset = safeRange.endOffset
                  } else if (endCompare >= 0) {
                    // Range ends after or at this node
                    endOffset = textNode.textContent?.length || 0
                  } else {
                    // Range ends within this node - calculate offset
                    const tempRange = safeRange.cloneRange()
                    tempRange.setEnd(nodeRange.endContainer, nodeRange.endOffset)
                    const textBeforeEnd = textNode.textContent?.substring(0, nodeRange.endOffset) || ''
                    endOffset = textBeforeEnd.length - tempRange.toString().length
                  }
                  
                  if (startOffset >= endOffset || startOffset < 0 || endOffset > (textNode.textContent?.length || 0)) {
                    continue // Invalid offsets
                  }
                } catch (e) {
                  continue
                }
                const text = textNode.textContent || ''
                
                // Split the text node
                const beforeText = text.substring(0, startOffset)
                const selectedText = text.substring(startOffset, endOffset)
                const afterText = text.substring(endOffset)
                
                // Create fragment with before text, highlight span, and after text
                const fragment = document.createDocumentFragment()
                if (beforeText.length > 0) {
                  fragment.appendChild(document.createTextNode(beforeText))
                }
                
                // Only create one mark for the first node, reuse for others
                const segmentMark = i === textNodes.length - 1 ? mark : mark.cloneNode(true) as HTMLElement
                segmentMark.textContent = selectedText
                fragment.appendChild(segmentMark)
                
                if (afterText.length > 0) {
                  fragment.appendChild(document.createTextNode(afterText))
                }
                
                // Store replacement for later
                replacements.push({ node: textNode, fragment, mark: segmentMark })
              }
              
              // Apply all replacements in reverse order (last to first)
              // This ensures we don't break parent-child relationships
              // Check each node is still valid before replacing
              for (let i = replacements.length - 1; i >= 0; i--) {
                const { node, fragment } = replacements[i]
                
                // Verify node is still in the DOM and has a valid parent
                if (!node.parentNode) {
                  continue
                }
                
                if (!node.isConnected) {
                  continue
                }
                
                const parent = node.parentNode
                
                // Double-check parent still contains this node
                if (!parent.contains(node)) {
                  continue
                }
                
                // Verify parent is still in the document
                if (!parent.isConnected) {
                  continue
                }
                
                try {
                  parent.replaceChild(fragment, node)
                } catch (e) {
                  // If replaceChild fails, the node might have been removed by React
                  // Continue with next replacement instead of breaking
                  continue
                }
              }
              
              // Add click handlers to all marks after they're in the DOM
              // Do this outside of batched updates to avoid issues
              setTimeout(() => {
                replacements.forEach(({ mark }) => {
                  if (mark.parentNode && !mark.hasAttribute('data-click-handler-added')) {
                    mark.setAttribute('data-click-handler-added', 'true')
                    mark.addEventListener('click', (e) => {
                      e.stopPropagation()
                      handleHighlightClick(note.id)
                    })
                  }
                })
              }, 0)
            }
          } catch (e) {
            return
          }

          // Add click handler (only if mark is still valid and wasn't already handled in multi-node case)
          if (mark.parentNode) {
            mark.addEventListener('click', (e) => {
              e.stopPropagation()
              handleHighlightClick(note.id)
            })
          }
        }
      })
      
      // Reset flag after a delay to allow React to settle
      setTimeout(() => {
        isApplyingHighlightsRef.current = false
      }, 100)
    }

    // Schedule the idle callback
    if ('requestIdleCallback' in window) {
      (window as any).requestIdleCallback(idleCallback, { timeout: 1000 })
    } else {
      // Fallback: use setTimeout with longer delay
      setTimeout(idleCallback, 200)
    }
  }, 500) // Increased delay to ensure React is done

        return () => clearTimeout(timeoutId)
      } catch (error) {
        isApplyingHighlightsRef.current = false
      }
    }

    // Schedule the work
    scheduleWork(applyHighlights)

    return () => {
      isApplyingHighlightsRef.current = false
    }
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

  // Find the markdown source text that corresponds to the selected rendered text
  // This function tries to locate where the rendered text appears in the markdown source
  const findMarkdownSourceText = (renderedText: string, markdownSource: string): string => {
    if (!renderedText || !markdownSource) return renderedText

    // Normalize the rendered text for comparison
    const normalizedRendered = normalizeText(renderedText)
    if (normalizedRendered.length === 0) return renderedText

    // Strategy 1: Try to find the exact rendered text in markdown source
    // This works when the text doesn't have markdown syntax
    const index = markdownSource.indexOf(renderedText)
    if (index !== -1) {
      // Found exact match - return the markdown source text at this position
      // Extract a bit more to include potential markdown syntax around it
      const start = Math.max(0, index - 20)
      const end = Math.min(markdownSource.length, index + renderedText.length + 20)
      const extracted = markdownSource.substring(start, end).trim()
      return extracted
    }

    // Strategy 2: Try normalized matching
    const normalizedMarkdown = normalizeText(markdownSource)
    const normalizedIndex = normalizedMarkdown.indexOf(normalizedRendered)
    
    if (normalizedIndex !== -1) {
      // Map normalized position back to markdown source
      let markdownPos = 0
      let normalizedPos = 0
      
      while (normalizedPos < normalizedIndex && markdownPos < markdownSource.length) {
        if (/\s/.test(markdownSource[markdownPos])) {
          markdownPos++
          while (markdownPos < markdownSource.length && /\s/.test(markdownSource[markdownPos])) {
            markdownPos++
          }
        } else {
          normalizedPos++
          markdownPos++
        }
      }
      
      // Now we need to find where the text starts and ends in the markdown source
      // Go backwards to find the start (to include opening markdown syntax like ** or *)
      let startPos = markdownPos
      while (startPos > 0 && startPos > markdownPos - 30) {
        const char = markdownSource[startPos - 1]
        // Stop at whitespace or if we hit markdown syntax that might be opening
        if (/\s/.test(char)) {
          // Check if there's markdown syntax before this whitespace
          if (startPos > 2 && /[*_`#]/.test(markdownSource[startPos - 2])) {
            startPos = Math.max(0, startPos - 3)
          }
          break
        }
        if (/[*_`#\[\]()]/.test(char)) {
          // Include markdown syntax
          startPos--
          break
        }
        startPos--
      }
      
      // Go forwards to find the end (to include closing markdown syntax)
      let endPos = markdownPos
      let remainingLength = normalizedRendered.length
      
      while (remainingLength > 0 && endPos < markdownSource.length) {
        const char = markdownSource[endPos]
        if (/\s/.test(char)) {
          endPos++
          while (endPos < markdownSource.length && /\s/.test(markdownSource[endPos])) {
            endPos++
          }
        } else {
          remainingLength--
          endPos++
        }
      }
      
      // Extend endPos to include potential closing markdown syntax
      while (endPos < markdownSource.length && /[*_`]/.test(markdownSource[endPos])) {
        endPos++
      }
      
      // Extract the markdown text with syntax
      const extracted = markdownSource.substring(startPos, endPos).trim()
      
      // Verify the extracted text contains the rendered text (after removing markdown)
      const extractedPlain = extracted
        .replace(/\*\*([^*]+)\*\*/g, '$1')
        .replace(/\*([^*]+)\*/g, '$1')
        .replace(/`([^`]+)`/g, '$1')
        .replace(/#+\s+/g, '')
        .replace(/\[([^\]]+)\]\([^\)]+\)/g, '$1')
        .trim()
      
      const normalizedExtracted = normalizeText(extractedPlain)
      
      if (normalizedExtracted.includes(normalizedRendered) || normalizedRendered.includes(normalizedExtracted)) {
        return extracted
      }
      
      // If verification failed, return a larger chunk
      const largerChunk = markdownSource.substring(
        Math.max(0, startPos - 10),
        Math.min(markdownSource.length, endPos + 10)
      ).trim()
      return largerChunk
    }

    // Strategy 3: Word-based matching
    const words = normalizedRendered.split(' ').filter(w => w.length > 2) // Filter out very short words
    if (words.length > 0) {
      // Try to find the first few significant words
      const searchWords = words.slice(0, Math.min(5, words.length))
      const searchPhrase = searchWords.join(' ')
      
      const phraseIndex = normalizedMarkdown.indexOf(searchPhrase)
      if (phraseIndex !== -1) {
        // Map back to markdown position
        let markdownPos = 0
        let normalizedPos = 0
        
        while (normalizedPos < phraseIndex && markdownPos < markdownSource.length) {
          if (/\s/.test(markdownSource[markdownPos])) {
            markdownPos++
            while (markdownPos < markdownSource.length && /\s/.test(markdownSource[markdownPos])) {
              markdownPos++
            }
          } else {
            normalizedPos++
            markdownPos++
          }
        }
        
        // Extract a chunk that likely contains the full text with markdown
        // Start a bit before to catch opening markdown syntax
        const start = Math.max(0, markdownPos - 20)
        // End a bit after to catch closing markdown syntax
        const end = Math.min(markdownSource.length, markdownPos + renderedText.length * 2 + 50)
        const extracted = markdownSource.substring(start, end).trim()
        return extracted
      }
    }

    // Fallback: return the rendered text (will be stored as-is)
    // This might work if the text is simple enough
    return renderedText
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
    }
  }

  const handleUpdateNote = async (noteId: string) => {
    if (!onUpdateNote) return
    
    try {
      await onUpdateNote(noteId, editingText)
      setEditingNoteId(null)
      setEditingText('')
    } catch (error) {
    }
  }

  const handleDeleteNote = async (noteId: string) => {
    if (!onDeleteNote) return
    
    try {
      await onDeleteNote(noteId)
      setActiveNoteId(null)
      setShowCommentBubble(false)
    } catch (error) {
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

