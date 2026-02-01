'use client'

import { useState, useEffect, useRef } from 'react'
import { MarkdownRenderer } from './MarkdownRenderer'
import { Button } from '@/components/ui/button'
import { 
  Eye, 
  Code, 
  Split,
  Bold,
  Italic,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  Quote,
  Link as LinkIcon,
  Code2
} from 'lucide-react'

interface SplitMarkdownEditorProps {
  content: string
  onChange: (content: string) => void
  placeholder?: string
  className?: string
}

type ViewMode = 'split' | 'editor' | 'preview'

export function SplitMarkdownEditor({ 
  content, 
  onChange, 
  placeholder = 'Start typing markdown...', 
  className = '' 
}: SplitMarkdownEditorProps) {
  const [markdown, setMarkdown] = useState(content || '')
  const [viewMode, setViewMode] = useState<ViewMode>('split')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    // Update markdown when content prop changes
    if (content !== markdown) {
      setMarkdown(content || '')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [content])

  const handleMarkdownChange = (value: string) => {
    setMarkdown(value)
    onChange(value)
  }

  // Auto-resize textarea
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = `${textareaRef.current.scrollHeight}px`
    }
  }, [markdown])

  // Insert markdown syntax at cursor position or wrap selection
  const insertMarkdown = (before: string, after: string = '', placeholder: string = '') => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selectedText = markdown.substring(start, end)
    const textBefore = markdown.substring(0, start)
    const textAfter = markdown.substring(end)

    let newText: string
    if (selectedText) {
      // Wrap selected text
      newText = textBefore + before + selectedText + after + textAfter
    } else {
      // Insert with placeholder
      const insertText = before + placeholder + after
      newText = textBefore + insertText + textAfter
    }

    setMarkdown(newText)
    onChange(newText)

    // Set cursor position after inserted text
    setTimeout(() => {
      if (textarea) {
        const newPosition = start + before.length + (selectedText || placeholder).length
        textarea.setSelectionRange(newPosition, newPosition)
        textarea.focus()
      }
    }, 0)
  }

  const insertHeading = (level: number) => {
    const hashes = '#'.repeat(level)
    insertMarkdown(`${hashes} `, '', 'Heading')
  }

  const insertBold = () => insertMarkdown('**', '**', 'bold text')
  const insertItalic = () => insertMarkdown('*', '*', 'italic text')
  const insertCode = () => insertMarkdown('`', '`', 'code')
  const insertCodeBlock = () => insertMarkdown('```\n', '\n```', 'code block')
  const insertUnorderedList = () => insertMarkdown('- ', '', 'List item')
  const insertOrderedList = () => insertMarkdown('1. ', '', 'List item')
  const insertBlockquote = () => insertMarkdown('> ', '', 'Quote')
  const insertLink = () => insertMarkdown('[', '](url)', 'link text')

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const textarea = textareaRef.current
      if (!textarea || document.activeElement !== textarea) return
      
      // Check for Ctrl/Cmd + key combinations
      if (e.ctrlKey || e.metaKey) {
        switch (e.key.toLowerCase()) {
          case 'b':
            e.preventDefault()
            insertBold()
            break
          case 'i':
            e.preventDefault()
            insertItalic()
            break
          case 'k':
            e.preventDefault()
            insertLink()
            break
        }
      }
    }

    const textarea = textareaRef.current
    if (textarea) {
      textarea.addEventListener('keydown', handleKeyDown)
      return () => {
        textarea.removeEventListener('keydown', handleKeyDown)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [markdown])

  return (
    <div className={`border border-gray-300 rounded-lg overflow-hidden flex flex-col ${className}`}>
      {/* Toolbar */}
      <div className="border-b border-gray-300 bg-gray-50 px-4 py-2 flex items-center justify-between">
        <div className="flex items-center gap-2 flex-1">
          <span className="text-sm font-medium text-gray-700 mr-2">Markdown Editor</span>
          
          {/* Formatting Buttons */}
          <div className="flex items-center gap-1 border-l border-gray-300 pl-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={insertBold}
              title="Bold (Ctrl+B)"
            >
              <Bold className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={insertItalic}
              title="Italic (Ctrl+I)"
            >
              <Italic className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={insertCode}
              title="Inline Code"
            >
              <Code className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={insertCodeBlock}
              title="Code Block"
            >
              <Code2 className="h-4 w-4" />
            </Button>

            <div className="w-px h-6 bg-gray-300 mx-1" />

            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => insertHeading(1)}
              title="Heading 1"
            >
              <Heading1 className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => insertHeading(2)}
              title="Heading 2"
            >
              <Heading2 className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => insertHeading(3)}
              title="Heading 3"
            >
              <Heading3 className="h-4 w-4" />
            </Button>

            <div className="w-px h-6 bg-gray-300 mx-1" />

            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={insertUnorderedList}
              title="Unordered List"
            >
              <List className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={insertOrderedList}
              title="Ordered List"
            >
              <ListOrdered className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={insertBlockquote}
              title="Blockquote"
            >
              <Quote className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={insertLink}
              title="Link"
            >
              <LinkIcon className="h-4 w-4" />
            </Button>
          </div>
        </div>
        
        {/* View Mode Buttons */}
        <div className="flex items-center gap-1 border-l border-gray-300 pl-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => setViewMode('split')}
            className={viewMode === 'split' ? 'bg-gray-200' : ''}
            title="Split View"
          >
            <Split className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => setViewMode('editor')}
            className={viewMode === 'editor' ? 'bg-gray-200' : ''}
            title="Editor Only"
          >
            <Code className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => setViewMode('preview')}
            className={viewMode === 'preview' ? 'bg-gray-200' : ''}
            title="Preview Only"
          >
            <Eye className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Editor and Preview Container */}
      <div className="flex flex-1 overflow-hidden" style={{ minHeight: '400px' }}>
        {/* Markdown Editor (Left) */}
        {(viewMode === 'split' || viewMode === 'editor') && (
          <div className={`flex flex-col ${viewMode === 'split' ? 'w-1/2' : 'w-full'} border-r border-gray-300`}>
            <div className="bg-gray-50 px-3 py-2 border-b border-gray-300">
              <span className="text-xs font-medium text-gray-600 uppercase">Markdown</span>
            </div>
            <div className="flex-1 overflow-auto bg-white">
              <textarea
                ref={textareaRef}
                value={markdown}
                onChange={(e) => handleMarkdownChange(e.target.value)}
                placeholder={placeholder}
                className="w-full h-full p-4 font-mono text-sm leading-relaxed resize-none border-0 focus:outline-none focus:ring-0"
                style={{ 
                  minHeight: '400px',
                  lineHeight: '1.6',
                  fontFamily: 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace'
                }}
                spellCheck={false}
              />
            </div>
          </div>
        )}

        {/* Preview (Right) */}
        {(viewMode === 'split' || viewMode === 'preview') && (
          <div className={`flex flex-col ${viewMode === 'split' ? 'w-1/2' : 'w-full'} bg-gray-50`}>
            <div className="bg-gray-50 px-3 py-2 border-b border-gray-300">
              <span className="text-xs font-medium text-gray-600 uppercase">Preview</span>
            </div>
            <div className="flex-1 overflow-auto p-4 bg-white">
              {markdown ? (
                <MarkdownRenderer content={markdown} />
              ) : (
                <div className="text-gray-400 text-sm italic">
                  {placeholder}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

