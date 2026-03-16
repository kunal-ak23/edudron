'use client'

import { useState, useCallback } from 'react'
import { Button } from '@/components/ui/button'

interface BudgetConfig {
  buckets: Array<{ id: string; label: string; min?: number; max?: number }>
  total?: number
}

interface BudgetAllocationInputProps {
  config: BudgetConfig
  onSubmit: (data: { input: Record<string, number> }) => void
  disabled?: boolean
}

export function BudgetAllocationInput({ config, onSubmit, disabled }: BudgetAllocationInputProps) {
  const total = config.total ?? 100
  const buckets = config.buckets ?? []

  const [allocations, setAllocations] = useState<Record<string, number>>(() => {
    const init: Record<string, number> = {}
    const perBucket = Math.floor(total / Math.max(1, buckets.length))
    buckets.forEach((b, i) => {
      init[b.id] = i === buckets.length - 1 ? total - perBucket * (buckets.length - 1) : perBucket
    })
    return init
  })

  const currentTotal = Object.values(allocations).reduce((sum, v) => sum + v, 0)

  const handleChange = useCallback((bucketId: string, value: number) => {
    setAllocations((prev) => {
      const bucket = buckets.find((b) => b.id === bucketId)
      const min = bucket?.min ?? 0
      const max = bucket?.max ?? total
      const clamped = Math.max(min, Math.min(max, value))
      return { ...prev, [bucketId]: clamped }
    })
  }, [buckets, total])

  const isValid = currentTotal === total

  return (
    <div className="space-y-5">
      {buckets.map((bucket) => {
        const value = allocations[bucket.id] ?? 0
        const pct = total > 0 ? Math.round((value / total) * 100) : 0

        return (
          <div key={bucket.id}>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-sm font-medium text-gray-700">{bucket.label}</label>
              <span className="text-sm font-mono text-gray-500">{value} ({pct}%)</span>
            </div>
            <input
              type="range"
              min={bucket.min ?? 0}
              max={bucket.max ?? total}
              value={value}
              onChange={(e) => handleChange(bucket.id, parseInt(e.target.value, 10))}
              disabled={disabled}
              className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-600 disabled:opacity-50"
            />
          </div>
        )
      })}

      <div className={`text-sm font-medium text-center py-2 rounded ${
        isValid ? 'text-green-700 bg-green-50' : 'text-amber-700 bg-amber-50'
      }`}>
        Total: {currentTotal} / {total}
        {!isValid && <span className="ml-1">(must equal {total})</span>}
      </div>

      <Button
        onClick={() => onSubmit({ input: allocations })}
        disabled={!isValid || disabled}
        className="w-full"
      >
        Confirm Allocation
      </Button>
    </div>
  )
}
