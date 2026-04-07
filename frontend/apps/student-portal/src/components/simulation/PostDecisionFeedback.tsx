'use client'

import type { MetricImpact } from '@kunal-ak23/edudron-shared-utils'

interface PostDecisionFeedbackProps {
  scoreDelta?: number
  impactDescription?: string
  metricImpacts?: MetricImpact[]
}

const directionIcon = (direction: string) => {
  if (direction === 'up') return '↑'
  if (direction === 'down') return '↓'
  return '→'
}

const magnitudeStyle = (magnitude: string) => {
  if (magnitude === 'strong') return 'text-sm font-bold'
  if (magnitude === 'moderate') return 'text-xs font-semibold'
  return 'text-xs font-medium'
}

const metricPillStyle = (direction: string) => {
  if (direction === 'up') return 'bg-green-900/30 border border-green-500/40 text-green-400'
  if (direction === 'down') return 'bg-red-900/30 border border-red-500/40 text-red-400'
  return 'bg-slate-800/50 border border-slate-600/40 text-slate-400'
}

const scoreBadgeStyle = (delta?: number) => {
  if (delta == null) return 'bg-slate-800 border border-slate-600 text-slate-400'
  if (delta >= 10) return 'bg-green-900/40 border border-green-500/50 text-green-400'
  if (delta >= 5) return 'bg-amber-900/40 border border-amber-500/50 text-amber-400'
  return 'bg-slate-800/50 border border-slate-600/40 text-slate-400'
}

export function PostDecisionFeedback({ scoreDelta, impactDescription, metricImpacts }: PostDecisionFeedbackProps) {
  if (scoreDelta == null && !impactDescription && (!metricImpacts || metricImpacts.length === 0)) {
    return null
  }

  return (
    <div className="space-y-3 p-4 bg-[#0d1526] border border-[#1E3A5F]/40 rounded-xl">
      {/* Score delta badge */}
      {scoreDelta != null && (
        <div className="flex items-center gap-2">
          <span className={`inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-bold ${scoreBadgeStyle(scoreDelta)}`}>
            {scoreDelta > 0 ? `+${scoreDelta}` : scoreDelta} pts
          </span>
        </div>
      )}

      {/* Impact description */}
      {impactDescription && (
        <p className="text-sm text-[#C8D5E8] leading-relaxed italic">
          {impactDescription}
        </p>
      )}

      {/* Metric impact pills */}
      {metricImpacts && metricImpacts.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {metricImpacts.map((m, i) => (
            <span key={i} className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full ${magnitudeStyle(m.magnitude)} ${metricPillStyle(m.direction)}`}>
              <span>{directionIcon(m.direction)}</span>
              <span className="uppercase tracking-wide">{m.metric}</span>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
