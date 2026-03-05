import { mergeAttributes } from '@tiptap/core'
import { Image } from '@tiptap/extension-image'

export type ImageAlignment = 'left' | 'center' | 'right'

/** Escape a string for safe use inside an HTML attribute (double-quoted). */
function escapeAttr(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    resizableImage: {
      setImageAlignment: (alignment: ImageAlignment) => ReturnType
    }
  }
}

export const ResizableImage = Image.extend({
  name: 'image',

  addAttributes() {
    return {
      ...this.parent?.(),
      alignment: {
        default: 'center',
        parseHTML: (element: HTMLElement) => {
          // Primary: read from data-alignment attribute
          const dataAlign = element.getAttribute('data-alignment')
          if (dataAlign && ['left', 'center', 'right'].includes(dataAlign)) {
            return dataAlign
          }
          // Fallback: infer alignment from inline style margins
          // This handles cases where tiptap-markdown strips data- attributes
          const style = element.getAttribute('style') || ''
          if (style.includes('margin-left: 0') || style.includes('margin-left:0')) {
            return 'left'
          }
          if (style.includes('margin-right: 0') || style.includes('margin-right:0')) {
            return 'right'
          }
          return 'center'
        },
        renderHTML: (attributes: Record<string, unknown>) => {
          return {
            'data-alignment': attributes.alignment || 'center',
          }
        },
      },
    }
  },

  renderHTML({ HTMLAttributes }) {
    const alignment = (HTMLAttributes['data-alignment'] || 'center') as ImageAlignment

    // Build img styles
    const styles: string[] = []
    if (HTMLAttributes.width) {
      styles.push(`width: ${HTMLAttributes.width}px`)
      styles.push('height: auto')
    }
    // Alignment via display block + margins
    styles.push('display: block')
    if (alignment === 'left') {
      styles.push('margin-right: auto')
      styles.push('margin-left: 0')
    } else if (alignment === 'right') {
      styles.push('margin-left: auto')
      styles.push('margin-right: 0')
    } else {
      styles.push('margin-left: auto')
      styles.push('margin-right: auto')
    }

    const { 'data-alignment': _, style: _existingStyle, ...restAttrs } = HTMLAttributes
    const mergedAttrs = mergeAttributes(this.options.HTMLAttributes, restAttrs, {
      'data-alignment': alignment,
      style: styles.join('; '),
    })

    return ['img', mergedAttrs]
  },

  addNodeView() {
    const parentNodeView = this.parent?.()

    // If resize is disabled or parent returns null, use default rendering
    if (!parentNodeView) {
      return undefined as any
    }

    // Wrap the parent's resize node view to add alignment styles to the wrapper
    return (props: any) => {
      const nodeView = typeof parentNodeView === 'function' ? parentNodeView(props) : parentNodeView

      if (nodeView && nodeView.dom) {
        const dom = nodeView.dom as HTMLElement

        const applyAlignment = (alignment: ImageAlignment) => {
          // Use width: fit-content so the wrapper only wraps the image content,
          // allowing margin-left/margin-right to control alignment.
          dom.style.width = 'fit-content'
          dom.style.display = 'block'
          if (alignment === 'left') {
            dom.style.marginRight = 'auto'
            dom.style.marginLeft = '0'
          } else if (alignment === 'right') {
            dom.style.marginLeft = 'auto'
            dom.style.marginRight = '0'
          } else {
            dom.style.marginLeft = 'auto'
            dom.style.marginRight = 'auto'
          }
        }

        // Apply initial alignment
        applyAlignment((props.node.attrs.alignment || 'center') as ImageAlignment)

        // Override update to handle alignment changes
        const originalUpdate = nodeView.update
        if (originalUpdate) {
          nodeView.update = (updatedNode: any, decorations: any, innerDecorations: any) => {
            const result = originalUpdate.call(nodeView, updatedNode, decorations, innerDecorations)
            // Always apply alignment regardless of parent update result,
            // since the parent may not handle our custom alignment attribute
            if (result !== false) {
              applyAlignment((updatedNode.attrs.alignment || 'center') as ImageAlignment)
            }
            return result
          }
        }
      }

      return nodeView
    }
  },

  addCommands() {
    return {
      ...this.parent?.(),
      setImageAlignment:
        (alignment: ImageAlignment) =>
        ({ commands }: any) => {
          return commands.updateAttributes('image', { alignment })
        },
    }
  },

  addStorage() {
    return {
      ...this.parent?.(),
      markdown: {
        serialize(state: any, node: any) {
          const src = node.attrs?.src ?? ''
          const alt = node.attrs?.alt ?? ''
          const title = node.attrs?.title ?? ''
          const width = node.attrs?.width
          const alignment = node.attrs?.alignment || 'center'

          // If image has custom width or non-default alignment, serialize as HTML <img> tag
          if (width || alignment !== 'center') {
            const styles: string[] = ['display: block', 'height: auto']
            if (width) styles.push(`width: ${width}px`)
            if (alignment === 'left') {
              styles.push('margin-right: auto', 'margin-left: 0')
            } else if (alignment === 'right') {
              styles.push('margin-left: auto', 'margin-right: 0')
            } else {
              styles.push('margin-left: auto', 'margin-right: auto')
            }
            const attrs = [
              `src="${escapeAttr(src)}"`,
              alt ? `alt="${escapeAttr(alt)}"` : '',
              title ? `title="${escapeAttr(title)}"` : '',
              width ? `width="${escapeAttr(String(width))}"` : '',
              `data-alignment="${escapeAttr(alignment)}"`,
              `style="${escapeAttr(styles.join('; '))}"`,
            ]
              .filter(Boolean)
              .join(' ')
            state.write(`<img ${attrs}>\n`)
          } else {
            // Standard markdown image syntax
            state.write(
              `![${state.esc(alt || '')}](${state.esc(src)}${title ? ` "${state.esc(title)}"` : ''})`
            )
          }
          state.closeBlock(node)
        },
        parse: {
          // HTML <img> tags are parsed by markdown-it's html option
        },
      },
    }
  },
})
