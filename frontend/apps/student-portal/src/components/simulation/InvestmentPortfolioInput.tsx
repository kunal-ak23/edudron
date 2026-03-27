'use client'

import { useState, useCallback } from 'react'

interface Department {
  id: string
  label: string
  description: string
  minAllocation: number
  maxAllocation: number
  projectedRoiRange: string
}

interface InvestmentConfig {
  totalBudget: number
  currency: string
  departments: Department[]
}

interface InvestmentPortfolioInputProps {
  config: InvestmentConfig
  onSubmit: (data: { input: Record<string, number> }) => void
  disabled?: boolean
}

function formatCurrency(amount: number, currency: string) {
  if (amount >= 1_000_000) return `${currency}${(amount / 1_000_000).toFixed(1)}M`
  if (amount >= 1_000) return `${currency}${(amount / 1_000).toFixed(0)}K`
  return `${currency}${amount.toLocaleString()}`
}

export function InvestmentPortfolioInput({ config, onSubmit, disabled }: InvestmentPortfolioInputProps) {
  const [allocations, setAllocations] = useState<Record<string, number>>(() => {
    const init: Record<string, number> = {}
    config.departments.forEach(d => { init[d.id] = d.minAllocation })
    return init
  })

  const totalAllocated = Object.values(allocations).reduce((sum, v) => sum + v, 0)
  const remaining = config.totalBudget - totalAllocated
  const isValid = remaining >= 0 && remaining <= config.totalBudget * 0.05 // Allow 5% unallocated

  const handleChange = useCallback((deptId: string, value: number) => {
    const dept = config.departments.find(d => d.id === deptId)
    if (!dept) return
    const clamped = Math.max(dept.minAllocation, Math.min(dept.maxAllocation, value))
    setAllocations(prev => ({ ...prev, [deptId]: clamped }))
  }, [config.departments])

  return (
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-5">
      <div className="flex items-center justify-between">
        <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
          Investment Portfolio
        </h3>
        <span className="text-xs uppercase tracking-widest text-[#6cd3f7] font-bold">
          {formatCurrency(config.totalBudget, config.currency)}
        </span>
      </div>

      {config.departments.map((dept) => {
        const value = allocations[dept.id] ?? dept.minAllocation
        return (
          <div key={dept.id} className="bg-[#1A2744] border border-white/5 rounded-xl p-4">
            <div className="flex items-center justify-between mb-1">
              <span className="text-sm font-medium text-[#dbe2fb]">{dept.label}</span>
              <span className="font-mono text-sm text-[#6cd3f7]">{formatCurrency(value, config.currency)}</span>
            </div>
            <p className="text-xs text-slate-400 mb-2">{dept.description}</p>
            <input
              type="range"
              min={dept.minAllocation}
              max={dept.maxAllocation}
              step={Math.max(1, Math.floor((dept.maxAllocation - dept.minAllocation) / 100))}
              value={value}
              onChange={(e) => handleChange(dept.id, parseInt(e.target.value, 10))}
              disabled={disabled}
              className="w-full h-1 appearance-none cursor-pointer rounded-full bg-[#2d3448] disabled:opacity-50 disabled:cursor-not-allowed"
              style={{ accentColor: '#6cd3f7' }}
            />
            <div className="flex justify-between text-xs text-slate-400 mt-1">
              <span>{formatCurrency(dept.minAllocation, config.currency)}</span>
              <span className="text-[#6cd3f7]/70">ROI: {dept.projectedRoiRange}</span>
              <span>{formatCurrency(dept.maxAllocation, config.currency)}</span>
            </div>
          </div>
        )
      })}

      <div className={`text-sm font-mono text-center py-2 rounded ${
        remaining < 0 ? 'text-red-400 bg-red-900/20' : remaining === 0 ? 'text-green-400 bg-green-900/20' : 'text-amber-400 bg-amber-900/20'
      }`}>
        Allocated: {formatCurrency(totalAllocated, config.currency)} / {formatCurrency(config.totalBudget, config.currency)}
        {remaining > 0 && <span className="ml-2">({formatCurrency(remaining, config.currency)} remaining)</span>}
        {remaining < 0 && <span className="ml-2">(Over budget!)</span>}
      </div>

      <button
        type="button"
        onClick={() => onSubmit({ input: allocations })}
        disabled={!isValid || disabled}
        className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
      >
        Confirm Allocation
      </button>
    </div>
  )
}
