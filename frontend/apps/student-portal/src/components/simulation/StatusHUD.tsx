'use client'

import { useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'

interface StatusHUDProps {
  role: string
  year: number
  totalYears: number
  decision: number
  totalDecisions: number
  budget?: number
  score: number
  performanceBand: string
}

function formatBudget(amount: number): string {
  if (amount >= 1_000_000) return `$${(amount / 1_000_000).toFixed(1)}M`
  if (amount >= 1_000) return `$${(amount / 1_000).toFixed(0)}K`
  return `$${amount.toLocaleString()}`
}

const bandColors: Record<string, string> = {
  THRIVING: '#22C55E',
  STEADY: '#F59E0B',
  STRUGGLING: '#EF4444',
}

export function StatusHUD({ role, year, totalYears, decision, totalDecisions, budget, score, performanceBand }: StatusHUDProps) {
  const [expanded, setExpanded] = useState(false)
  const progress = totalDecisions > 0 ? (decision / totalDecisions) * 100 : 0
  const bandColor = bandColors[performanceBand] || bandColors.STEADY

  return (
    <div className="bg-[#0F1729] border-t border-[#1E3A5F]/30">
      {/* Always-visible thin bar (mobile) / full bar (desktop) */}
      <div
        className="flex items-center justify-between gap-3 px-4 py-2 max-w-4xl mx-auto xl:cursor-default cursor-pointer"
        onClick={() => setExpanded((v) => !v)}
        role="button"
        aria-expanded={expanded}
      >
        {/* Role Badge */}
        <div className="flex items-center gap-2 shrink-0">
          <span className="text-xs bg-[#0891B2]/20 text-[#0891B2] px-2 py-0.5 rounded-full truncate max-w-[120px]">
            {role}
          </span>
        </div>

        {/* Center: compact on mobile, full on desktop */}
        <div className="flex-1 text-center min-w-0">
          {/* Mobile compact: Y1 D3/6 */}
          <div className="xl:hidden text-xs text-[#94A3B8] font-mono">
            Y{year} D{decision}/{totalDecisions}
          </div>
          {/* Desktop full */}
          <div className="hidden xl:block text-xs text-[#94A3B8]">
            Year {year}/{totalYears} | Decision {decision}/{totalDecisions}
          </div>
          <div className="w-full bg-[#1A2744] rounded-full h-1 mt-1">
            <div
              className="bg-[#0891B2] h-1 rounded-full transition-all duration-300"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>

        {/* Right: Score + Band */}
        <div className="flex items-center gap-2 shrink-0">
          <div className="flex items-center gap-1.5">
            <span
              className="w-2 h-2 rounded-full shrink-0"
              style={{ backgroundColor: bandColor }}
            />
            <span className="font-mono text-xs text-[#E2E8F0]">{score}</span>
          </div>
          {/* Expand toggle on mobile only */}
          <span className="xl:hidden text-[#94A3B8]">
            {expanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
          </span>
        </div>
      </div>

      {/* Expanded details — mobile only */}
      {expanded && (
        <div className="xl:hidden border-t border-[#1E3A5F]/30 px-4 py-3 grid grid-cols-3 gap-3 text-center max-w-4xl mx-auto">
          <div>
            <div className="text-[10px] uppercase font-bold text-[#879298] mb-0.5">Year</div>
            <div className="text-sm font-bold text-[#E2E8F0]">{year}/{totalYears}</div>
          </div>
          <div>
            <div className="text-[10px] uppercase font-bold text-[#879298] mb-0.5">Status</div>
            <div className="text-sm font-bold" style={{ color: bandColor }}>{performanceBand}</div>
          </div>
          {budget !== undefined && (
            <div>
              <div className="text-[10px] uppercase font-bold text-[#879298] mb-0.5">Budget</div>
              <div className="text-sm font-bold text-[#6cd3f7]">{formatBudget(budget)}</div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
