import { Mark, mergeAttributes } from '@tiptap/core'

export interface HighlightMarkOptions {
  HTMLAttributes: Record<string, unknown>
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    highlightMark: {
      setHighlight: (attributes: { id: string; color: string; opacity?: number }) => ReturnType
      unsetHighlight: () => ReturnType
    }
  }
}

export const HighlightMark = Mark.create<HighlightMarkOptions>({
  name: 'highlightMark',

  addOptions() {
    return {
      HTMLAttributes: {},
    }
  },

  addAttributes() {
    return {
      id: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-highlight-id'),
        renderHTML: (attributes) => ({
          'data-highlight-id': attributes.id,
        }),
      },
      color: {
        default: '#FFEB3B',
        parseHTML: (element) => element.getAttribute('data-highlight-color'),
        renderHTML: (attributes) => ({
          'data-highlight-color': attributes.color,
        }),
      },
      opacity: {
        default: 0.4,
        parseHTML: (element) => {
          const val = element.getAttribute('data-highlight-opacity')
          return val ? parseFloat(val) : 0.4
        },
        renderHTML: (attributes) => ({
          'data-highlight-opacity': String(attributes.opacity),
        }),
      },
    }
  },

  parseHTML() {
    return [
      {
        tag: 'mark[data-highlight]',
      },
    ]
  },

  renderHTML({ HTMLAttributes }) {
    const color = HTMLAttributes['data-highlight-color'] || '#FFEB3B'
    const opacity = HTMLAttributes['data-highlight-opacity'] || 0.4

    return [
      'mark',
      mergeAttributes(this.options.HTMLAttributes, HTMLAttributes, {
        'data-highlight': '',
        'data-note-id': HTMLAttributes['data-highlight-id'],
        style: `background-color: ${color}; opacity: 1; background-clip: padding-box; --highlight-color: ${color}; --highlight-opacity: ${opacity};`,
      }),
      0,
    ]
  },

  addCommands() {
    return {
      setHighlight:
        (attributes) =>
        ({ commands }) => {
          return commands.setMark(this.name, attributes)
        },
      unsetHighlight:
        () =>
        ({ commands }) => {
          return commands.unsetMark(this.name)
        },
    }
  },
})
