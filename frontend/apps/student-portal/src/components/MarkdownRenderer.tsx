'use client'

import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeSanitize from 'rehype-sanitize'

interface MarkdownRendererProps {
  content: string
  className?: string
}

const looksLikeHtml = (value: string) => /<\/?[a-z][\s\S]*>/i.test(value)

// Minimal HTML -> Markdown conversion for content produced by rich-text editors.
// This is intentionally conservative (no scripts/styles) and targets common tags we store.
const htmlToMarkdown = (html: string) => {
  let md = html

  // Normalize line breaks and remove empty paragraphs
  md = md.replace(/\r\n/g, '\n')
  md = md.replace(/<p>\s*<\/p>/gi, '')

  // Flatten common "li > p" structure (prevents extra spacing)
  md = md.replace(/<li>\s*<p>/gi, '<li>')
  md = md.replace(/<\/p>\s*<\/li>/gi, '</li>')

  // Links
  md = md.replace(
    /<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi,
    (_m, href, text) => `[${String(text).replace(/<[^>]+>/g, '').trim()}](${href})`
  )

  // Inline formatting
  md = md.replace(/<(strong|b)>([\s\S]*?)<\/\1>/gi, '**$2**')
  md = md.replace(/<(em|i)>([\s\S]*?)<\/\1>/gi, '*$2*')

  // Structural tags
  md = md.replace(/<br\s*\/?>/gi, '\n')
  md = md.replace(/<\/p>/gi, '\n\n')
  md = md.replace(/<p[^>]*>/gi, '')
  md = md.replace(/<ul[^>]*>/gi, '\n')
  md = md.replace(/<\/ul>/gi, '\n')
  md = md.replace(/<ol[^>]*>/gi, '\n')
  md = md.replace(/<\/ol>/gi, '\n')
  md = md.replace(/<li[^>]*>/gi, '- ')
  md = md.replace(/<\/li>/gi, '\n')

  // Drop any remaining tags
  md = md.replace(/<\/?[^>]+>/g, '')

  // Clean up whitespace
  md = md.replace(/[ \t]+\n/g, '\n')
  md = md.replace(/\n{3,}/g, '\n\n')
  return md.trim()
}

export function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  if (!content) {
    return null
  }

  const normalizedContent = looksLikeHtml(content) ? htmlToMarkdown(content) : content

  // Configure sanitize to allow links with target and rel attributes
  const sanitizeConfig = {
    tagNames: [
      'p', 'br', 'strong', 'em', 'u', 's', 'del', 'ins',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'ul', 'ol', 'li',
      'blockquote', 'code', 'pre',
      'a', 'img',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'hr', 'div', 'span'
    ],
    attributes: {
      a: ['href', 'title', 'target', 'rel', 'class', 'nofollow'],
      img: ['src', 'alt', 'title', 'width', 'height'],
      '*': ['class']
    },
    protocols: {
      a: { href: ['http', 'https', 'mailto'] },
      img: { src: ['http', 'https', 'data'] }
    }
  }

  return (
    <div className={`prose prose-lg max-w-none ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeSanitize, sanitizeConfig]]}
        components={{
          h1: ({ node, ...props }) => <h1 className="text-3xl font-bold mt-6 mb-4 text-gray-900" {...props} />,
          h2: ({ node, ...props }) => <h2 className="text-2xl font-bold mt-5 mb-3 text-gray-900" {...props} />,
          h3: ({ node, ...props }) => <h3 className="text-xl font-semibold mt-4 mb-2 text-gray-900" {...props} />,
          h4: ({ node, ...props }) => <h4 className="text-lg font-semibold mt-3 mb-2 text-gray-900" {...props} />,
          ul: ({ node, ...props }) => <ul className="list-disc list-inside my-4 space-y-2 text-gray-900" {...props} />,
          ol: ({ node, ...props }) => <ol className="list-decimal list-inside my-4 space-y-2 text-gray-900" {...props} />,
          li: ({ node, ...props }) => <li className="ml-4 text-gray-900" {...props} />,
          code: ({ node, inline, ...props }: any) => {
            if (inline) {
              return (
                <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm font-mono text-gray-900" {...props} />
              )
            }
            return (
              <code className="block bg-gray-100 text-gray-900 p-4 rounded-lg overflow-x-auto my-4 font-mono text-sm" {...props} />
            )
          },
          pre: ({ node, ...props }) => <pre className="bg-gray-100 text-gray-900 p-4 rounded-lg overflow-x-auto my-4 font-mono text-sm" {...props} />,
          blockquote: ({ node, ...props }) => (
            <blockquote className="border-l-4 border-primary-500 pl-4 italic my-4 text-gray-700" {...props} />
          ),
          a: ({ node, ...props }) => (
            <a className="text-primary-600 hover:text-primary-700 underline font-medium" target="_blank" rel="noopener noreferrer" {...props} />
          ),
          p: ({ node, ...props }) => <p className="my-4 leading-relaxed text-gray-900 text-base font-normal" {...props} />,
          strong: ({ node, ...props }) => <strong className="font-semibold text-gray-900" {...props} />,
          em: ({ node, ...props }) => <em className="italic text-gray-900" {...props} />,
          table: ({ node, ...props }) => (
            <div className="overflow-x-auto my-4">
              <table className="min-w-full border-collapse border border-gray-300" {...props} />
            </div>
          ),
          th: ({ node, ...props }) => (
            <th className="border border-gray-300 px-4 py-2 bg-gray-50 font-semibold text-left text-gray-900" {...props} />
          ),
          td: ({ node, ...props }) => (
            <td className="border border-gray-300 px-4 py-2 text-gray-900" {...props} />
          ),
          hr: ({ node, ...props }) => <hr className="my-6 border-t border-gray-300" {...props} />,
        }}
      >
        {normalizedContent}
      </ReactMarkdown>
    </div>
  )
}


