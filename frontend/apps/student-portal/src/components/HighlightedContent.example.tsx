/**
 * Example: How to integrate HighlightedContent component
 * 
 * This file shows how to use the HighlightedContent component
 * in your learning page.
 */

'use client'

import React, { useState } from 'react'
import { HighlightedContent } from './HighlightedContent'

export function ExampleLearnPage() {
  const [showSidebar, setShowSidebar] = useState(false)
  const lectureId = 'lecture-123'
  const studentId = 'student-456'
  const markdownContent = `
# Introduction to Machine Learning

Machine learning is a subset of artificial intelligence that focuses on 
the development of algorithms and statistical models that enable computer 
systems to improve their performance on a specific task through experience.

## Key Concepts

1. **Supervised Learning**: Learning with labeled data
2. **Unsupervised Learning**: Finding patterns in unlabeled data
3. **Reinforcement Learning**: Learning through interaction with an environment

### Supervised Learning

In supervised learning, the algorithm learns from a training dataset 
that contains both input features and corresponding output labels.
  `.trim()

  return (
    <div className="max-w-4xl mx-auto p-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Course Content</h1>
        <button
          onClick={() => setShowSidebar(!showSidebar)}
          className={`px-4 py-2 text-sm font-medium border rounded-md transition-colors ${
            showSidebar
              ? 'bg-primary-600 text-white border-primary-600'
              : 'text-primary-600 hover:text-primary-700 border-primary-600'
          }`}
        >
          {showSidebar ? 'Hide Notes' : 'View Notes'}
        </button>
      </div>

      <HighlightedContent
        content={markdownContent}
        isMarkdown={true}
        documentId={lectureId}
        userId={studentId}
        showSidebar={showSidebar}
        onSidebarToggle={setShowSidebar}
        className="bg-white rounded-lg p-6 shadow-sm"
      />
    </div>
  )
}

/**
 * Example: Using with HTML content instead of markdown
 */
export function ExampleHTMLContent() {
  const [showSidebar, setShowSidebar] = useState(false)
  const htmlContent = `
    <div>
      <h1>HTML Content</h1>
      <p>This is <strong>HTML</strong> content that can also be highlighted.</p>
      <ul>
        <li>Item 1</li>
        <li>Item 2</li>
      </ul>
    </div>
  `

  return (
    <HighlightedContent
      content={htmlContent}
      isMarkdown={false}
      documentId="content-123"
      userId="user-456"
      showSidebar={showSidebar}
      onSidebarToggle={setShowSidebar}
    />
  )
}

/**
 * Example: Custom highlight click handler
 */
export function ExampleWithCustomHandler() {
  const handleHighlightClick = (highlightId: string) => {
    // Do something custom, e.g., show a modal, navigate, etc.
  }

  return (
    <HighlightedContent
      content="# Example\n\nSome content here."
      isMarkdown={true}
      documentId="doc-123"
      userId="user-456"
      onHighlightClick={handleHighlightClick}
    />
  )
}

