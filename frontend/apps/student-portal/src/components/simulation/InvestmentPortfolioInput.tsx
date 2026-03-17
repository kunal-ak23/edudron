'use client'

import { useState, useCallback } from 'react'
import { Button } from '@/components/ui/button'

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
    <div className="space-y-5">
      <div className="flex items-center justify-between text-sm">
        <span className="text-[#94A3B8]">Total Budget</span>
        <span className="font-mono text-[#E2E8F0]">{formatCurrency(config.totalBudget, config.currency)}</span>
      </div>

      {config.departments.map((dept) => {
        const value = allocations[dept.id] ?? dept.minAllocation
        return (
          <div key={dept.id} className="bg-[#1A2744] border border-[#1E3A5F]/30 rounded-lg p-4">
            <div className="flex items-center justify-between mb-1">
              <span className="text-sm font-medium text-[#E2E8F0]">{dept.label}</span>
              <span className="font-mono text-sm text-[#0891B2]">{formatCurrency(value, config.currency)}</span>
            </div>
            <p className="text-xs text-[#94A3B8] mb-2">{dept.description}</p>
            <input
              type="range"
              min={dept.minAllocation}
              max={dept.maxAllocation}
              step={Math.max(1, Math.floor((dept.maxAllocation - dept.minAllocation) / 100))}
              value={value}
              onChange={(e) => handleChange(dept.id, parseInt(e.target.value, 10))}
              disabled={disabled}
              className="w-full h-2 bg-[#0F1729] rounded-lg appearance-none cursor-pointer accent-[#0891B2] disabled:opacity-50"
            />
            <div className="flex justify-between text-xs text-[#94A3B8] mt-1">
              <span>{formatCurrency(dept.minAllocation, config.currency)}</span>
              <span className="text-[#0891B2]/70">ROI: {dept.projectedRoiRange}</span>
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
