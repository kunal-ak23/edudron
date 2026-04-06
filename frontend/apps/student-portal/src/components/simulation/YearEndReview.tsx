'use client'

import { Building2, Users, TrendingUp, ArrowUpRight, ArrowDownRight, Link2, AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { YearEndReviewDTO } from '@kunal-ak23/edudron-shared-utils'

interface YearEndReviewProps {
  review: YearEndReviewDTO
  currentYear: number
  onContinue: () => void
  continuing?: boolean
}

const BAND_STYLES: Record<string, { bg: string; text: string; border: string; label: string }> = {
  THRIVING: { bg: 'bg-green-900/20', text: 'text-green-400', border: 'border-green-500/30', label: 'Thriving' },
  STEADY: { bg: 'bg-amber-900/20', text: 'text-amber-400', border: 'border-amber-500/30', label: 'Steady' },
  STRUGGLING: { bg: 'bg-red-900/20', text: 'text-red-400', border: 'border-red-500/30', label: 'Struggling' },
}

const STAKEHOLDER_CONFIG: Record<string, { icon: typeof Building2; label: string; color: string }> = {
  board: { icon: Building2, label: 'Board', color: 'text-[#0891B2]' },
  customers: { icon: Users, label: 'Customers', color: 'text-amber-400' },
  investors: { icon: TrendingUp, label: 'Investors', color: 'text-[#F97316]' },
}

const IMPACT_STYLES: Record<string, string> = {
  positive: 'bg-green-900/20 border-green-500/30 text-green-400',
  negative: 'bg-red-900/20 border-red-500/30 text-red-400',
  neutral: 'bg-slate-800/50 border-slate-600/30 text-slate-400',
}

export function YearEndReview({ review, currentYear, onContinue, continuing }: YearEndReviewProps) {
  const bandStyle = BAND_STYLES[review.band] || BAND_STYLES.STEADY

  return (
    <div className="w-full max-w-2xl mx-auto space-y-6">
      {/* Header */}
      <div className="text-center space-y-3">
        <h1 className="text-2xl font-bold text-[#E2E8F0]">Year {review.year} Review</h1>
        <div
          className={`inline-flex items-center gap-2 px-4 py-1.5 rounded-full border ${bandStyle.bg} ${bandStyle.border}`}
        >
          <span className={`text-sm font-semibold ${bandStyle.text}`}>{bandStyle.label}</span>
        </div>
      </div>

      {/* Metrics dashboard */}
      {review.metrics && Object.keys(review.metrics).length > 0 && (
        <div>
          <h2 className="text-xs font-semibold text-[#94A3B8] uppercase tracking-wide mb-3">
            Key Metrics
          </h2>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
            {Object.entries(review.metrics).map(([key, metric]: [string, any]) => {
              const value = typeof metric === 'object' ? metric.value : metric
              const trend = typeof metric === 'object' ? metric.trend : undefined
              const isPositive = trend === 'up' || trend === 'positive'
              const isNegative = trend === 'down' || trend === 'negative'

              return (
                <div
                  key={key}
                  className="flex flex-col gap-1 p-4 bg-[#1A2744] border border-[#1E3A5F]/30 rounded-xl"
                >
                  <span className="text-xs font-medium text-[#94A3B8] uppercase tracking-wide">
                    {key.replace(/_/g, ' ')}
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="text-xl font-bold text-[#E2E8F0] tabular-nums font-mono">
                      {typeof value === 'number' ? value.toLocaleString() : String(value)}
                    </span>
                    {isPositive && <ArrowUpRight className="h-4 w-4 text-green-400" />}
                    {isNegative && <ArrowDownRight className="h-4 w-4 text-red-400" />}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Stakeholder feedback */}
      {review.feedback && Object.keys(review.feedback).length > 0 && (
        <div>
          <h2 className="text-xs font-semibold text-[#94A3B8] uppercase tracking-wide mb-3">
            Stakeholder Feedback
          </h2>
          <div className="space-y-3">
            {Object.entries(review.feedback).map(([stakeholder, quote]) => {
              const config = STAKEHOLDER_CONFIG[stakeholder.toLowerCase()] || {
                icon: Users,
                label: stakeholder,
                color: 'text-[#94A3B8]',
              }
              const Icon = config.icon

              return (
                <div
                  key={stakeholder}
                  className="flex gap-4 p-4 bg-[#1A2744] border border-[#1E3A5F]/30 rounded-xl"
                >
                  <div className={`shrink-0 mt-0.5 ${config.color}`}>
                    <Icon className="h-5 w-5" />
                  </div>
                  <div>
                    <span className="text-sm font-semibold text-[#E2E8F0]">{config.label}</span>
                    <p className="text-sm text-[#94A3B8] mt-1 italic leading-relaxed">
                      &ldquo;{quote}&rdquo;
                    </p>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Warning banner — STRUGGLING band */}
      {review.warningSignal && (
        <div className="flex items-start gap-3 p-4 bg-amber-900/20 border border-amber-500/30 rounded-xl animate-pulse-subtle">
          <AlertTriangle className="h-5 w-5 text-amber-400 shrink-0 mt-0.5" />
          <p className="text-sm text-amber-300 leading-relaxed">{review.warningSignal}</p>
        </div>
      )}

      {/* Decision Highlights */}
      {review.decisionHighlights && review.decisionHighlights.length > 0 && (
        <div>
          <h2 className="text-xs font-semibold text-[#94A3B8] uppercase tracking-wide mb-3">
            Your Key Decisions This Year
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {review.decisionHighlights.map((h) => (
              <div
                key={h.decisionId}
                className="p-3 bg-[#1A2744] border border-[#1E3A5F]/30 rounded-xl"
              >
                <div className="flex items-center justify-between gap-2 mb-1.5">
                  <span className="text-sm font-semibold text-[#E2E8F0] truncate">{h.label}</span>
                  <span className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded-full border shrink-0 ${IMPACT_STYLES[h.impact] || IMPACT_STYLES.neutral}`}>
                    {h.impact}
                  </span>
                </div>
                <p className="text-xs text-[#94A3B8] leading-relaxed">{h.summary}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Cross-Decision Insight */}
      {review.crossDecisionInsight && (
        <div className="flex items-start gap-3 p-4 border-l-2 border-[#0891B2] bg-[#0891B2]/5 rounded-r-xl">
          <Link2 className="h-4 w-4 text-[#0891B2] shrink-0 mt-0.5" />
          <p className="text-sm text-[#94A3B8] leading-relaxed italic">{review.crossDecisionInsight}</p>
        </div>
      )}

      {/* Continue button */}
      <div className="flex justify-center pt-2 pb-4">
        <Button size="lg" onClick={onContinue} disabled={continuing} className="px-10">
          {continuing ? 'Advancing...' : `Continue to Year ${currentYear + 1}`}
        </Button>
      </div>
    </div>
  )
}
