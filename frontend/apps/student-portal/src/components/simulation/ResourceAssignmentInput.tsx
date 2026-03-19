'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Minus, Plus } from 'lucide-react'

interface ResourceConfig {
  buckets: Array<{ id: string; label: string; description?: string }>
  totalTokens: number
}

interface ResourceAssignmentInputProps {
  config: ResourceConfig
  onSubmit: (data: { input: Record<string, number> }) => void
  disabled?: boolean
}

export function ResourceAssignmentInput({ config, onSubmit, disabled }: ResourceAssignmentInputProps) {
  const totalTokens = config.totalTokens ?? 10
  const buckets = config.buckets ?? []

  const [assignments, setAssignments] = useState<Record<string, number>>(() => {
    const init: Record<string, number> = {}
    buckets.forEach((b) => { init[b.id] = 0 })
    return init
  })

  const used = Object.values(assignments).reduce((sum, v) => sum + v, 0)
  const remaining = totalTokens - used

  const increment = (bucketId: string) => {
    if (remaining <= 0 || disabled) return
    setAssignments((prev) => ({ ...prev, [bucketId]: (prev[bucketId] ?? 0) + 1 }))
  }

  const decrement = (bucketId: string) => {
    if (disabled) return
    setAssignments((prev) => ({
      ...prev,
      [bucketId]: Math.max(0, (prev[bucketId] ?? 0) - 1)
    }))
  }

  return (
    <div className="space-y-3">
      <div className={`text-sm font-medium text-center py-2 rounded ${
        remaining === 0 ? 'text-green-700 bg-green-50' : 'text-gray-600 bg-gray-50'
      }`}>
        Tokens remaining: <span className="font-mono">{remaining}</span> / {totalTokens}
      </div>

      {buckets.map((bucket) => {
        const count = assignments[bucket.id] ?? 0
        return (
          <Card key={bucket.id}>
            <CardContent className="p-4 flex items-center gap-4">
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium">{bucket.label}</div>
                {bucket.description && (
                  <div className="text-xs text-gray-500 mt-0.5">{bucket.description}</div>
                )}
              </div>
              <div className="flex items-center gap-2 flex-shrink-0">
                <button
                  type="button"
                  onClick={() => decrement(bucket.id)}
                  disabled={count <= 0 || disabled}
                  className="w-8 h-8 rounded-full border border-gray-300 flex items-center justify-center hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                  aria-label={`Remove token from ${bucket.label}`}
                >
                  <Minus className="h-3.5 w-3.5" />
                </button>
                <span className="w-8 text-center font-mono font-semibold text-sm">{count}</span>
                <button
                  type="button"
                  onClick={() => increment(bucket.id)}
                  disabled={remaining <= 0 || disabled}
                  className="w-8 h-8 rounded-full border border-gray-300 flex items-center justify-center hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                  aria-label={`Add token to ${bucket.label}`}
                >
                  <Plus className="h-3.5 w-3.5" />
                </button>
              </div>
            </CardContent>
          </Card>
        )
      })}

      <div className="pt-2">
        <Button
          onClick={() => onSubmit({ input: assignments })}
          disabled={remaining !== 0 || disabled}
          className="w-full"
        >
          Confirm Assignment
        </Button>
      </div>
    </div>
  )
}
