'use client'

import { Building2, Users, TrendingUp, ArrowUpRight, ArrowDownRight, Award } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { YearEndReviewDTO } from '@kunal-ak23/edudron-shared-utils'

interface YearEndReviewProps {
  review: YearEndReviewDTO
  currentYear: number
  onContinue: () => void
  continuing?: boolean
}

const BAND_STYLES: Record<string, { bg: string; text: string; border: string; label: string }> = {
  THRIVING: { bg: 'bg-green-50', text: 'text-green-700', border: 'border-green-300', label: 'Thriving' },
  STEADY: { bg: 'bg-yellow-50', text: 'text-yellow-700', border: 'border-yellow-300', label: 'Steady' },
  STRUGGLING: { bg: 'bg-red-50', text: 'text-red-700', border: 'border-red-300', label: 'Struggling' },
}

const STAKEHOLDER_CONFIG: Record<string, { icon: typeof Building2; label: string; color: string }> = {
  board: { icon: Building2, label: 'Board', color: 'text-indigo-600' },
  customers: { icon: Users, label: 'Customers', color: 'text-teal-600' },
  investors: { icon: TrendingUp, label: 'Investors', color: 'text-amber-600' },
}

export function YearEndReview({ review, currentYear, onContinue, continuing }: YearEndReviewProps) {
  const bandStyle = BAND_STYLES[review.band] || BAND_STYLES.STEADY

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center px-4 py-8 md:py-12">
      <div className="w-full max-w-2xl space-y-8">
        {/* Header */}
        <div className="text-center space-y-3">
          <h1 className="text-3xl font-bold text-gray-900">Year {review.year} Review</h1>
          <div
            className={`inline-flex items-center gap-2 px-4 py-2 rounded-full border ${bandStyle.bg} ${bandStyle.border}`}
          >
            <span className={`text-sm font-semibold ${bandStyle.text}`}>{bandStyle.label}</span>
          </div>
        </div>

        {/* Promotion banner */}
        {review.promotionTitle && (
          <div className="flex items-center justify-center gap-3 px-6 py-4 bg-gradient-to-r from-amber-50 to-yellow-50 border border-amber-200 rounded-xl">
            <Award className="h-6 w-6 text-amber-600" />
            <span className="text-lg font-semibold text-amber-800">
              Promoted to {review.promotionTitle}!
            </span>
          </div>
        )}

        {/* Metrics dashboard */}
        {review.metrics && Object.keys(review.metrics).length > 0 && (
          <div>
            <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">
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
                    className="flex flex-col gap-1 p-4 bg-white border border-gray-200 rounded-xl"
                  >
                    <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">
                      {key.replace(/_/g, ' ')}
                    </span>
                    <div className="flex items-center gap-2">
                      <span className="text-xl font-bold text-gray-900 tabular-nums">
                        {typeof value === 'number' ? value.toLocaleString() : String(value)}
                      </span>
                      {isPositive && <ArrowUpRight className="h-4 w-4 text-green-600" />}
                      {isNegative && <ArrowDownRight className="h-4 w-4 text-red-500" />}
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
            <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">
              Stakeholder Feedback
            </h2>
            <div className="space-y-3">
              {Object.entries(review.feedback).map(([stakeholder, quote]) => {
                const config = STAKEHOLDER_CONFIG[stakeholder.toLowerCase()] || {
                  icon: Users,
                  label: stakeholder,
                  color: 'text-gray-600',
                }
                const Icon = config.icon

                return (
                  <div
                    key={stakeholder}
                    className="flex gap-4 p-4 bg-white border border-gray-200 rounded-xl"
                  >
                    <div className={`shrink-0 mt-0.5 ${config.color}`}>
                      <Icon className="h-5 w-5" />
                    </div>
                    <div>
                      <span className="text-sm font-semibold text-gray-700">{config.label}</span>
                      <p className="text-sm text-gray-600 mt-1 italic leading-relaxed">
                        &ldquo;{quote}&rdquo;
                      </p>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* Continue button */}
        <div className="flex justify-center pt-4 pb-8">
          <Button size="lg" onClick={onContinue} disabled={continuing} className="px-10">
            {continuing ? 'Advancing...' : `Continue to Year ${currentYear + 1}`}
          </Button>
        </div>
      </div>
    </div>
  )
}
