'use client'

import { useState, useCallback, useMemo } from 'react'

interface BudgetConfig {
  buckets: Array<{ id: string; label: string; min?: number; max?: number }>
  total?: number
  totalBudget?: number
}

interface BudgetAllocationInputProps {
  config: BudgetConfig
  onSubmit: (data: { input: Record<string, number> }) => void
  disabled?: boolean
}

function formatAmount(value: number, total: number, totalBudget: number): string {
  const dollars = total > 0 ? (value / total) * totalBudget : 0
  if (dollars >= 1_000_000) return `$${(dollars / 1_000_000).toFixed(1)}M`
  if (dollars >= 1_000) return `$${(dollars / 1_000).toFixed(0)}K`
  return `$${dollars.toFixed(0)}`
}

function formatBudget(amount: number): string {
  if (amount >= 1_000_000) return `$${(amount / 1_000_000).toFixed(1)}M`
  if (amount >= 1_000) return `$${(amount / 1_000).toFixed(0)}K`
  return `$${amount.toFixed(0)}`
}

export function BudgetAllocationInput({ config, onSubmit, disabled }: BudgetAllocationInputProps) {
  const total = config.total ?? 100
  const totalBudget = config.totalBudget ?? total
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
  const surplus = total - currentTotal
  const isValid = currentTotal === total

  const extremeBuckets = useMemo(() => {
    const threshold = total * 0.3
    const extreme = new Set<string>()
    for (const bucket of buckets) {
      if ((allocations[bucket.id] ?? 0) > threshold) {
        extreme.add(bucket.id)
      }
    }
    return extreme
  }, [allocations, buckets, total])

  const hasExtremeAllocation = extremeBuckets.size > 0

  const handleChange = useCallback((bucketId: string, value: number) => {
    setAllocations((prev) => {
      const bucket = buckets.find((b) => b.id === bucketId)
      const min = bucket?.min ?? 0
      const max = bucket?.max ?? total
      const clamped = Math.max(min, Math.min(max, value))
      return { ...prev, [bucketId]: clamped }
    })
  }, [buckets, total])

  const resetToEqual = useCallback(() => {
    const perBucket = Math.floor(total / Math.max(1, buckets.length))
    const init: Record<string, number> = {}
    buckets.forEach((b, i) => {
      init[b.id] = i === buckets.length - 1 ? total - perBucket * (buckets.length - 1) : perBucket
    })
    setAllocations(init)
  }, [buckets, total])

  return (
    <div className="bg-[#222a3d] rounded-xl p-6 space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
          Budget Allocation
        </h3>
        <button
          type="button"
          onClick={resetToEqual}
          disabled={disabled}
          className="text-[10px] uppercase tracking-widest text-[#6cd3f7] hover:text-[#9be0fa] transition-colors font-bold disabled:opacity-50"
        >
          Reset to Equal
        </button>
      </div>

      {/* Extreme allocation warning */}
      {hasExtremeAllocation && (
        <div className="flex items-center gap-2 px-3 py-2 bg-[#93000a]/20 border border-[#ffb4ab]/20 rounded-lg">
          <svg className="w-4 h-4 text-[#ffb4ab] shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
          <span className="text-[11px] font-bold text-[#ffb4ab] uppercase tracking-wider">
            Extreme Allocation Warning
          </span>
        </div>
      )}

      {/* Sliders */}
      <div className="space-y-5">
        {buckets.map((bucket) => {
          const value = allocations[bucket.id] ?? 0
          const pct = total > 0 ? Math.round((value / total) * 100) : 0
          const isExtreme = extremeBuckets.has(bucket.id)

          return (
            <div key={bucket.id} className="space-y-1.5">
              {/* Label row */}
              <div className="flex items-center justify-between">
                <label className="text-xs uppercase tracking-widest text-slate-400 font-medium">
                  {bucket.label}
                </label>
                <div className="flex items-center gap-2">
                  <span
                    className={`text-lg font-bold ${isExtreme ? 'text-[#ffb4ab]' : 'text-[#6cd3f7]'}`}
                    style={{ fontFamily: 'Space Grotesk, sans-serif' }}
                  >
                    {formatAmount(value, total, totalBudget)}
                  </span>
                  <span className={`text-xs font-mono ${isExtreme ? 'text-[#ffb4ab]/70' : 'text-slate-500'}`}>
                    {pct}%
                  </span>
                </div>
              </div>

              {/* Slider */}
              <input
                type="range"
                min={bucket.min ?? 0}
                max={bucket.max ?? total}
                value={value}
                onChange={(e) => handleChange(bucket.id, parseInt(e.target.value, 10))}
                disabled={disabled}
                className={`w-full h-1 appearance-none cursor-pointer rounded-full disabled:opacity-50 disabled:cursor-not-allowed ${
                  isExtreme ? 'bg-[#93000a]' : 'bg-[#2d3448]'
                }`}
                style={{ accentColor: isExtreme ? '#ffb4ab' : '#6cd3f7' }}
              />

              {/* Range indicators */}
              <div className="flex justify-between text-[8px] uppercase tracking-tighter select-none">
                <span className="text-slate-600">Underfunded</span>
                <span className="text-[#6cd3f7]/60">Optimal Range</span>
                <span className="text-slate-600">High Risk</span>
              </div>
            </div>
          )
        })}
      </div>

      {/* Totals */}
      <div className="flex items-center justify-between pt-3 border-t border-white/5">
        <div className="space-y-0.5">
          <div className="text-[10px] uppercase tracking-widest text-slate-500 font-bold">Total Allocated</div>
          <div
            className={`text-lg font-bold ${isValid ? 'text-[#6cd3f7]' : 'text-[#F59E0B]'}`}
            style={{ fontFamily: 'Space Grotesk, sans-serif' }}
          >
            {formatBudget(totalBudget > total ? (currentTotal / total) * totalBudget : currentTotal)}
          </div>
        </div>
        <div className="space-y-0.5 text-right">
          <div className="text-[10px] uppercase tracking-widest text-slate-500 font-bold">Treasury Surplus</div>
          <div
            className={`text-lg font-bold ${surplus > 0 ? 'text-[#F59E0B]' : surplus === 0 ? 'text-[#6cd3f7]' : 'text-[#ffb4ab]'}`}
            style={{ fontFamily: 'Space Grotesk, sans-serif' }}
          >
            {formatBudget(totalBudget > total ? (surplus / total) * totalBudget : surplus)}
          </div>
        </div>
      </div>

      {!isValid && (
        <div className="text-center text-xs text-[#F59E0B]">
          Allocation must total {total} (currently {currentTotal})
        </div>
      )}

      {/* Submit */}
      <button
        type="button"
        onClick={() => onSubmit({ input: allocations })}
        disabled={!isValid || disabled}
        className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100"
      >
        Commit Strategy
      </button>
    </div>
  )
}
