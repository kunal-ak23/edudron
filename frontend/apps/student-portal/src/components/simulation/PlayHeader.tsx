'use client'

interface PlayHeaderProps {
  currentRole: string
  currentYear: number
  currentDecision: number
  totalDecisions: number
  cumulativeScore: number
}

export function PlayHeader({
  currentRole,
  currentYear,
  currentDecision,
  totalDecisions,
  cumulativeScore,
}: PlayHeaderProps) {
  return (
    <div className="flex items-center justify-between px-4 py-2 bg-white/80 backdrop-blur-sm border-b border-gray-200">
      <div className="flex items-center gap-3">
        <span className="text-sm font-medium text-gray-700">{currentRole}</span>
        <span className="text-gray-300">|</span>
        <span className="text-sm text-gray-500">
          Year {currentYear} — Decision {currentDecision} of {totalDecisions}
        </span>
      </div>

      <div className="flex items-center gap-4">
        {/* Decision progress dots */}
        <div className="flex items-center gap-1">
          {Array.from({ length: totalDecisions }, (_, i) => (
            <div
              key={i}
              className={`w-2 h-2 rounded-full transition-colors ${
                i < currentDecision
                  ? 'bg-primary-600'
                  : i === currentDecision
                    ? 'bg-primary-400 animate-pulse'
                    : 'bg-gray-200'
              }`}
            />
          ))}
        </div>

        <div className="text-sm text-gray-500">
          Score: <span className="font-semibold text-gray-800 tabular-nums">{cumulativeScore}</span> pts
        </div>
      </div>
    </div>
  )
}
