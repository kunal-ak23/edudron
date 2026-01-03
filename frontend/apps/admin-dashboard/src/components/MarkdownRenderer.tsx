'use client'

import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeRaw from 'rehype-raw'
import rehypeSanitize from 'rehype-sanitize'

interface MarkdownRendererProps {
  content: string
  className?: string
}

export function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  if (!content) {
    return null
  }

  return (
    <div className={`prose prose-sm max-w-none ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeRaw, rehypeSanitize]}
        components={{
          // Customize heading styles
          h1: ({ node, ...props }) => <h1 className="text-3xl font-bold mt-6 mb-4" {...props} />,
          h2: ({ node, ...props }) => <h2 className="text-2xl font-semibold mt-5 mb-3" {...props} />,
          h3: ({ node, ...props }) => <h3 className="text-xl font-semibold mt-4 mb-2" {...props} />,
          h4: ({ node, ...props }) => <h4 className="text-lg font-medium mt-3 mb-2" {...props} />,
          // Customize list styles
          ul: ({ node, ...props }) => <ul className="list-disc list-inside my-4 space-y-2" {...props} />,
          ol: ({ node, ...props }) => <ol className="list-decimal list-inside my-4 space-y-2" {...props} />,
          li: ({ node, ...props }) => <li className="ml-4" {...props} />,
          // Customize code blocks
          code: ({ node, inline, ...props }: any) => {
            if (inline) {
              return (
                <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm font-mono" {...props} />
              )
            }
            return (
              <code className="block bg-gray-900 text-gray-100 p-4 rounded-lg overflow-x-auto my-4" {...props} />
            )
          },
          pre: ({ node, ...props }) => <pre className="bg-gray-900 text-gray-100 p-4 rounded-lg overflow-x-auto my-4" {...props} />,
          // Customize blockquotes
          blockquote: ({ node, ...props }) => (
            <blockquote className="border-l-4 border-blue-500 pl-4 italic my-4 text-gray-700" {...props} />
          ),
          // Customize links
          a: ({ node, ...props }) => (
            <a className="text-blue-600 hover:text-blue-800 underline" target="_blank" rel="noopener noreferrer" {...props} />
          ),
          // Customize paragraphs
          p: ({ node, ...props }) => <p className="my-3 leading-relaxed" {...props} />,
          // Customize tables
          table: ({ node, ...props }) => (
            <div className="overflow-x-auto my-4">
              <table className="min-w-full border-collapse border border-gray-300" {...props} />
            </div>
          ),
          th: ({ node, ...props }) => (
            <th className="border border-gray-300 px-4 py-2 bg-gray-100 font-semibold text-left" {...props} />
          ),
          td: ({ node, ...props }) => (
            <td className="border border-gray-300 px-4 py-2" {...props} />
          ),
          // Customize horizontal rules
          hr: ({ node, ...props }) => <hr className="my-6 border-t border-gray-300" {...props} />,
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}

