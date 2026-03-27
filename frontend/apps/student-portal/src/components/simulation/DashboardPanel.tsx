'use client'

import React from 'react'

interface DashboardPanelProps {
  score: number
  maxScore?: number
  budget: number
  role: string
  performanceBand: string
  currentYear: number
  totalYears: number
  currentDecision: number
  totalDecisions: number
  decisionHistory?: Array<{
    year: number
    decision: number
    label: string
    quality: 'GOOD' | 'MEDIUM' | 'BAD'
    points: number
  }>
  goodDecisionCount?: number
  badDecisionCount?: number
  neutralDecisionCount?: number
  keyInsights?: string[]
}

function formatBudget(amount: number): string {
  if (amount >= 1_000_000) return `$${(amount / 1_000_000).toFixed(1)}M`
  if (amount >= 1_000) return `$${(amount / 1_000).toFixed(0)}K`
  return `$${amount}`
}

function bandColor(band: string): string {
  switch (band?.toUpperCase()) {
    case 'THRIVING': return '#6cd3f7'
    case 'STEADY': return '#F59E0B'
    case 'STRUGGLING': return '#ffb4ab'
    default: return '#879298'
  }
}

function qualityIcon(quality: string) {
  switch (quality) {
    case 'GOOD':
      return <span className="w-6 h-6 rounded-full bg-[#6cd3f7]/20 border border-[#6cd3f7] flex items-center justify-center text-[#6cd3f7] text-xs">✓</span>
    case 'BAD':
      return <span className="w-6 h-6 rounded-full bg-[#ffb4ab]/20 border border-[#ffb4ab] flex items-center justify-center text-[#ffb4ab] text-xs">✗</span>
    default:
      return <span className="w-6 h-6 rounded-full bg-[#8b9199]/20 border border-[#8b9199] flex items-center justify-center text-[#8b9199] text-xs">△</span>
  }
}

export default function DashboardPanel({
  score, maxScore = 2000, budget, role, performanceBand,
  currentYear, totalYears, currentDecision, totalDecisions,
  decisionHistory = [], goodDecisionCount = 0, badDecisionCount = 0,
  neutralDecisionCount = 0, keyInsights = []
}: DashboardPanelProps) {
  const scorePercent = maxScore > 0 ? Math.min((score / maxScore) * 100, 100) : 0

  return (
    <div className="p-6 space-y-8">
      {/* Score */}
      <section className="space-y-6">
        <div className="space-y-4">
          <div className="flex justify-between items-end">
            <span className="text-[11px] uppercase font-bold tracking-widest text-[#879298]">Simulation Score</span>
            <span className="text-2xl font-bold text-[#6cd3f7]" style={{ fontFamily: 'Space Grotesk, sans-serif' }}>
              {score.toLocaleString()} <span className="text-xs font-normal text-[#bdc8ce]">PTS</span>
            </span>
          </div>
          <div className="h-1 w-full bg-[#2d3448] rounded-full overflow-hidden">
            <div className="h-full bg-[#6cd3f7] transition-all duration-500" style={{ width: `${scorePercent}%` }} />
          </div>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-2 gap-4">
          <div className="p-3 bg-[#060e1f] rounded-lg">
            <div className="text-[10px] uppercase font-bold text-[#879298] mb-1">Liquid Capital</div>
            <div className="text-lg font-bold text-[#6cd3f7] tracking-tight" style={{ fontFamily: 'Space Grotesk, sans-serif' }}>
              {formatBudget(budget)}
            </div>
          </div>
          <div className="p-3 bg-[#060e1f] rounded-lg">
            <div className="text-[10px] uppercase font-bold text-[#879298] mb-1">Status</div>
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full animate-pulse" style={{ backgroundColor: bandColor(performanceBand) }} />
              <span className="text-[11px] font-bold" style={{ color: bandColor(performanceBand) }}>
                {performanceBand || 'STEADY'}
              </span>
            </div>
          </div>
        </div>

        {/* Role Badge */}
        <div className="flex justify-center">
          <span className="px-4 py-1.5 bg-[#6cd3f7]/10 border border-[#6cd3f7]/20 text-[#6cd3f7] rounded-full text-[11px] font-bold uppercase tracking-widest">
            {role || 'Analyst'}
          </span>
        </div>
      </section>

      {/* Decision History */}
      {decisionHistory.length > 0 && (
        <section className="space-y-4">
          <div className="text-[11px] uppercase font-bold tracking-widest text-[#879298]">Decision History</div>
          <div className="relative space-y-6 before:absolute before:left-[11px] before:top-2 before:bottom-2 before:w-[1px] before:bg-white/10">
            {decisionHistory.map((d, idx) => (
              <div key={idx} className="relative pl-8">
                <span className="absolute left-0 top-1">{qualityIcon(d.quality)}</span>
                <div className="text-sm font-medium text-[#dbe2fb]">{d.label}</div>
                <div className="text-[10px] text-[#bdc8ce] uppercase">
                  {d.quality === 'GOOD' ? 'Success' : d.quality === 'BAD' ? 'Poor Choice' : 'Neutral Impact'} {d.points > 0 ? `+${d.points} PTS` : ''}
                </div>
              </div>
            ))}
            {/* Current decision indicator */}
            <div className="relative pl-8">
              <span className="absolute left-0 top-1 w-6 h-6 rounded-full bg-[#6cd3f7] flex items-center justify-center animate-pulse">
                <span className="w-2 h-2 rounded-full bg-[#003543]" />
              </span>
              <div className="text-sm font-bold text-[#6cd3f7]">Pending Decision</div>
              <div className="text-[10px] text-[#bdc8ce] uppercase">Awaiting response...</div>
            </div>
          </div>
        </section>
      )}

      {/* Risk Level Warning */}
      {badDecisionCount > 0 && (
        <section className="p-4 bg-[#93000a]/20 rounded-xl border border-[#ffb4ab]/20">
          <div className="flex items-center gap-2 mb-2">
            <svg className="w-4 h-4 text-[#ffb4ab]" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            <span className="text-[11px] font-bold text-[#ffb4ab] uppercase tracking-wider">
              Risk Level: {badDecisionCount >= 3 ? 'Critical' : badDecisionCount >= 2 ? 'Elevated' : 'Moderate'}
            </span>
          </div>
          <div className="flex gap-1 mb-3">
            {Array.from({ length: 5 }, (_, i) => (
              <div key={i} className={`h-1.5 flex-1 rounded-full ${i < badDecisionCount ? 'bg-[#ffb4ab]' : 'bg-[#2d3448]'}`} />
            ))}
          </div>
          <p className="text-[10px] text-[#ffdad6] leading-tight">
            {badDecisionCount} bad decision{badDecisionCount !== 1 ? 's' : ''} made. {Math.max(0, 4 - badDecisionCount)} more consecutive bad decisions could lead to termination.
          </p>
        </section>
      )}
    </div>
  )
}
