import React from 'react'
import { BubbleMenu } from '@tiptap/react/menus'
import type { Editor } from '@tiptap/react'
import type { ImageAlignment } from './ResizableImage'

interface ImageBubbleMenuProps {
  editor: Editor
}

export function ImageBubbleMenu({ editor }: ImageBubbleMenuProps) {
  const shouldShow = ({ editor }: { editor: Editor }) => {
    return editor.isActive('image')
  }

  const currentAlignment = (editor.getAttributes('image').alignment || 'center') as ImageAlignment

  const setAlignment = (alignment: ImageAlignment) => {
    editor.chain().focus().setImageAlignment(alignment).run()
  }

  return (
    <BubbleMenu
      editor={editor}
      shouldShow={shouldShow}
      options={{
        placement: 'top',
      }}
    >
      <div className="flex items-center gap-1 bg-white rounded-lg shadow-lg border border-gray-200 p-1">
        {/* Align Left */}
        <button
          type="button"
          onClick={() => setAlignment('left')}
          className={`p-1.5 rounded hover:bg-gray-100 transition-colors ${
            currentAlignment === 'left' ? 'bg-blue-100 text-blue-600' : 'text-gray-600'
          }`}
          title="Align left"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="17" y1="10" x2="3" y2="10" />
            <line x1="21" y1="6" x2="3" y2="6" />
            <line x1="21" y1="14" x2="3" y2="14" />
            <line x1="17" y1="18" x2="3" y2="18" />
          </svg>
        </button>

        {/* Align Center */}
        <button
          type="button"
          onClick={() => setAlignment('center')}
          className={`p-1.5 rounded hover:bg-gray-100 transition-colors ${
            currentAlignment === 'center' ? 'bg-blue-100 text-blue-600' : 'text-gray-600'
          }`}
          title="Align center"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="10" x2="6" y2="10" />
            <line x1="21" y1="6" x2="3" y2="6" />
            <line x1="21" y1="14" x2="3" y2="14" />
            <line x1="18" y1="18" x2="6" y2="18" />
          </svg>
        </button>

        {/* Align Right */}
        <button
          type="button"
          onClick={() => setAlignment('right')}
          className={`p-1.5 rounded hover:bg-gray-100 transition-colors ${
            currentAlignment === 'right' ? 'bg-blue-100 text-blue-600' : 'text-gray-600'
          }`}
          title="Align right"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="21" y1="10" x2="7" y2="10" />
            <line x1="21" y1="6" x2="3" y2="6" />
            <line x1="21" y1="14" x2="3" y2="14" />
            <line x1="21" y1="18" x2="7" y2="18" />
          </svg>
        </button>

        {/* Separator */}
        <div className="w-px h-5 bg-gray-200 mx-0.5" />

        {/* Size info display */}
        {editor.getAttributes('image').width && (
          <span className="text-xs text-gray-500 px-1">
            {Math.round(editor.getAttributes('image').width)}px
          </span>
        )}
      </div>
    </BubbleMenu>
  )
}
