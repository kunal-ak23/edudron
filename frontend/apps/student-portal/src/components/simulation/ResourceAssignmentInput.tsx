'use client'

import { useState } from 'react'
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
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
          Resource Assignment
        </h3>
        <span className={`text-xs uppercase tracking-widest font-bold ${
          remaining === 0 ? 'text-green-400' : 'text-[#6cd3f7]'
        }`}>
          {remaining} / {totalTokens} remaining
        </span>
      </div>

      {buckets.map((bucket) => {
        const count = assignments[bucket.id] ?? 0
        return (
          <div key={bucket.id} className="bg-[#1A2744] border border-white/5 rounded-xl p-4 flex items-center gap-4">
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-[#dbe2fb]">{bucket.label}</div>
              {bucket.description && (
                <div className="text-xs text-slate-400 mt-0.5">{bucket.description}</div>
              )}
            </div>
            <div className="flex items-center gap-2 flex-shrink-0">
              <button
                type="button"
                onClick={() => decrement(bucket.id)}
                disabled={count <= 0 || disabled}
                className="w-8 h-8 rounded-full border border-white/10 flex items-center justify-center hover:bg-white/5 disabled:opacity-30 disabled:cursor-not-allowed transition-colors text-slate-400"
                aria-label={`Remove token from ${bucket.label}`}
              >
                <Minus className="h-3.5 w-3.5" />
              </button>
              <span className="w-8 text-center font-mono font-bold text-sm text-[#6cd3f7]">{count}</span>
              <button
                type="button"
                onClick={() => increment(bucket.id)}
                disabled={remaining <= 0 || disabled}
                className="w-8 h-8 rounded-full border border-white/10 flex items-center justify-center hover:bg-white/5 disabled:opacity-30 disabled:cursor-not-allowed transition-colors text-slate-400"
                aria-label={`Add token to ${bucket.label}`}
              >
                <Plus className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )
      })}

      <div className="pt-1">
        <button
          type="button"
          onClick={() => onSubmit({ input: assignments })}
          disabled={remaining !== 0 || disabled}
          className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
        >
          Confirm Assignment
        </button>
      </div>
    </div>
  )
}
