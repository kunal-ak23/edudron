'use client'

import React from 'react'

type ThinkingMode = 'ai' | 'saving' | 'finalizing' | null

function labelFor(mode: ThinkingMode) {
  if (mode === 'saving') return 'Saving your response…'
  if (mode === 'finalizing') return 'Finalizing…'
  return 'AI assistant is thinking…'
}

export function ThinkingPlaceholder({
  mode,
  subtitle,
  compact = false
}: {
  mode: ThinkingMode
  subtitle?: string
  compact?: boolean
}) {
  return (
    <div className={compact ? 'py-2' : 'py-3'}>
      <div className="flex items-center gap-3 text-sm text-gray-700">
        <div className="flex items-center gap-1.5">
          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
        </div>
        <div className="font-medium">{labelFor(mode)}</div>
      </div>

      <div className={compact ? 'mt-3 space-y-2' : 'mt-4 space-y-3'}>
        <div className="h-4 bg-gray-100 rounded animate-pulse w-[92%]" />
        <div className="h-4 bg-gray-100 rounded animate-pulse w-[84%]" />
        <div className="h-4 bg-gray-100 rounded animate-pulse w-[70%]" />
      </div>

      {(subtitle || !compact) && (
        <div className="mt-3 text-xs text-gray-500">
          {subtitle || 'Generating the next question based on your answers.'}
        </div>
      )}
    </div>
  )
}

