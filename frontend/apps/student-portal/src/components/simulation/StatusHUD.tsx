'use client'

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
  const progress = totalDecisions > 0 ? (decision / totalDecisions) * 100 : 0

  return (
    <div className="bg-[#0F1729] border-t border-[#1E3A5F]/30 px-4 py-2">
      <div className="flex items-center justify-between gap-4 max-w-4xl mx-auto">
        {/* Role Badge */}
        <div className="flex items-center gap-2 shrink-0">
          <span className="text-xs bg-[#0891B2]/20 text-[#0891B2] px-2 py-0.5 rounded-full truncate max-w-[140px]">
            {role}
          </span>
        </div>

        {/* Center: Year + Decision + Progress */}
        <div className="flex-1 text-center min-w-0">
          <div className="text-xs text-[#94A3B8]">
            Year {year}/{totalYears} | Decision {decision}/{totalDecisions}
          </div>
          <div className="w-full bg-[#1A2744] rounded-full h-1 mt-1">
            <div
              className="bg-[#0891B2] h-1 rounded-full transition-all duration-300"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>

        {/* Right: Budget + Score + Band */}
        <div className="flex items-center gap-3 shrink-0">
          {budget !== undefined && budget > 0 && (
            <span className="font-mono text-xs text-[#E2E8F0]">{formatBudget(budget)}</span>
          )}
          <div className="flex items-center gap-1.5">
            <span
              className="w-2 h-2 rounded-full"
              style={{ backgroundColor: bandColors[performanceBand] || bandColors.STEADY }}
            />
            <span className="font-mono text-xs text-[#E2E8F0]">{score}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
