'use client'

import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import { ResizableImage } from '@kunal-ak23/edudron-shared-utils'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import { Markdown } from 'tiptap-markdown'
import { useEffect } from 'react'
import '@kunal-ak23/edudron-shared-utils/tiptap/editor-styles.css'

interface MarkdownRendererProps {
  content: string
  className?: string
}

export function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
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
          class: 'text-blue-600 hover:text-blue-800 underline',
          target: '_blank',
          rel: 'noopener noreferrer',
        },
      }),
      ResizableImage.configure({
        HTMLAttributes: {
          class: 'max-w-full h-auto rounded',
        },
      }),
      Table.configure({ resizable: false }),
      TableRow,
      TableHeader,
      TableCell,
      Markdown.configure({
        html: true,
      }),
    ],
    content: '',
    editorProps: {
      attributes: {
        class: `prose prose-sm max-w-none ${className}`,
      },
    },
  })

  useEffect(() => {
    if (!editor || !content) return

    // tiptap-markdown handles both HTML and markdown content
    editor.commands.setContent(content)
  }, [content, editor])

  if (!content) return null

  if (!editor) {
    return (
      <div className={`prose prose-sm max-w-none ${className}`}>
        <p className="text-gray-400">Loading...</p>
      </div>
    )
  }

  return <EditorContent editor={editor} />
}
