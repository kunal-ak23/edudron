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
    consequenceTag?: string
  }>
  goodDecisionCount?: number
  badDecisionCount?: number
  neutralDecisionCount?: number
  keyInsights?: string[]
  consecutiveStruggling?: number
  budgetHistory?: number[]
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

function BudgetSparkline({ values }: { values: number[] }) {
  if (values.length < 2) return null
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const w = 80
  const h = 24
  const pad = 2
  const points = values.map((v, i) => {
    const x = pad + (i / (values.length - 1)) * (w - pad * 2)
    const y = h - pad - ((v - min) / range) * (h - pad * 2)
    return `${x},${y}`
  }).join(' ')
  const lastVal = values[values.length - 1]
  const prevVal = values[values.length - 2]
  const lineColor = lastVal >= prevVal ? '#6cd3f7' : '#ffb4ab'
  return (
    <svg width={w} height={h} className="overflow-visible">
      <polyline points={points} fill="none" stroke={lineColor} strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={points.split(' ').pop()?.split(',')[0]} cy={points.split(' ').pop()?.split(',')[1]} r="2" fill={lineColor} />
    </svg>
  )
}

export default function DashboardPanel({
  score, maxScore, budget, role, performanceBand,
  currentYear, totalYears, currentDecision, totalDecisions,
  decisionHistory = [], goodDecisionCount = 0, badDecisionCount = 0,
  neutralDecisionCount = 0, keyInsights = [],
  consecutiveStruggling = 0, budgetHistory = []
}: DashboardPanelProps) {
  // Year progress: how many decisions completed in current year
  const yearProgress = totalDecisions > 0 ? Math.min(((currentDecision) / totalDecisions) * 100, 100) : 0

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
          <div className="h-2 w-full bg-[#2d3448] rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-[#6cd3f7] to-[#0891B2] rounded-full transition-all duration-700 ease-out"
              style={{ width: `${yearProgress}%`, boxShadow: yearProgress > 0 ? '0 0 8px rgba(108,211,247,0.5)' : 'none' }}
            />
          </div>
          <p className="text-[10px] text-[#879298] text-right">Year {currentYear} — {currentDecision}/{totalDecisions} decisions</p>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-2 gap-4">
          <div className="p-3 bg-[#060e1f] rounded-lg">
            <div className="text-[10px] uppercase font-bold text-[#879298] mb-1">Liquid Capital</div>
            <div className="text-lg font-bold text-[#6cd3f7] tracking-tight" style={{ fontFamily: 'Space Grotesk, sans-serif' }}>
              {formatBudget(budget)}
            </div>
            {budgetHistory.length >= 2 && (
              <div className="mt-1.5">
                <BudgetSparkline values={budgetHistory} />
              </div>
            )}
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
                {d.consequenceTag && (
                  <div className="text-[9px] text-[#879298] mt-0.5 truncate max-w-[200px]">
                    #{d.consequenceTag}
                  </div>
                )}
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

      {/* Risk Level — always visible */}
      <section className={`p-4 rounded-xl border transition-colors duration-500 ${
        consecutiveStruggling >= 1 ? 'bg-amber-900/20 border-amber-500/30' :
        badDecisionCount >= 3 ? 'bg-[#93000a]/30 border-[#ffb4ab]/30' :
        badDecisionCount >= 1 ? 'bg-[#93000a]/20 border-[#ffb4ab]/20' :
        'bg-[#0F1729]/50 border-white/5'
      }`}>
        <div className="flex items-center gap-2 mb-2">
          <svg className={`w-4 h-4 ${badDecisionCount > 0 ? 'text-[#ffb4ab]' : 'text-emerald-400'}`} fill="currentColor" viewBox="0 0 20 20">
            {badDecisionCount > 0 ? (
              <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            ) : (
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
            )}
          </svg>
          <span className={`text-[11px] font-bold uppercase tracking-wider ${
            badDecisionCount >= 3 ? 'text-[#ffb4ab]' : badDecisionCount >= 1 ? 'text-[#ffb4ab]' : 'text-emerald-400'
          }`}>
            Risk Level: {badDecisionCount >= 3 ? 'Critical' : badDecisionCount >= 2 ? 'Elevated' : badDecisionCount >= 1 ? 'Moderate' : 'Safe'}
          </span>
        </div>
        <div className="flex gap-1 mb-3">
          {Array.from({ length: 5 }, (_, i) => (
            <div key={i} className={`h-1.5 flex-1 rounded-full transition-colors duration-500 ${i < badDecisionCount ? 'bg-[#ffb4ab]' : 'bg-[#2d3448]'}`} />
          ))}
        </div>
        <p className={`text-[10px] leading-tight ${consecutiveStruggling >= 1 ? 'text-amber-300' : badDecisionCount > 0 ? 'text-[#ffdad6]' : 'text-slate-400'}`}>
          {consecutiveStruggling >= 1
            ? '1 more struggling year = career over'
            : badDecisionCount === 0
              ? 'No bad decisions yet. Keep making thoughtful choices!'
              : `${badDecisionCount} bad decision${badDecisionCount !== 1 ? 's' : ''} made. ${Math.max(0, 4 - badDecisionCount)} more consecutive bad decisions could lead to termination.`}
        </p>
      </section>
    </div>
  )
}
