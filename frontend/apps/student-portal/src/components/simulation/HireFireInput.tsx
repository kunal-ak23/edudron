'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { ChevronDown, ChevronUp, AlertTriangle } from 'lucide-react'

interface Candidate {
  id: string
  name: string
  title: string
  stats: Record<string, string>
  salary: number
  bio: string
  strengths: string[]
  weaknesses: string[]
}

interface HireFireConfig {
  action: 'hire' | 'fire'
  budgetLimit: number
  candidates: Candidate[]
}

interface HireFireInputProps {
  config: HireFireConfig
  onSubmit: (data: { input: { selected: string } }) => void
  disabled?: boolean
}

export function HireFireInput({ config, onSubmit, disabled }: HireFireInputProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [expandedId, setExpandedId] = useState<string | null>(null)

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-sm text-[#94A3B8]">
        <span>Budget: <span className="font-mono text-[#E2E8F0]">${config.budgetLimit.toLocaleString()}</span></span>
        <span className="text-[#1E3A5F]">|</span>
        <span>{config.action === 'hire' ? 'Select a candidate to hire' : 'Select a team member to let go'}</span>
      </div>

      <div className="space-y-3">
        {config.candidates.map((candidate) => {
          const overBudget = candidate.salary > config.budgetLimit
          const isSelected = selectedId === candidate.id
          const isExpanded = expandedId === candidate.id

          return (
            <div
              key={candidate.id}
              className={`bg-[#1A2744] border rounded-xl overflow-hidden transition-all ${
                isSelected ? 'border-[#0891B2] ring-1 ring-[#0891B2]' : 'border-[#1E3A5F]/30 hover:border-[#1E3A5F]'
              } ${disabled ? 'opacity-60 pointer-events-none' : ''}`}
            >
              <div
                className="p-4 cursor-pointer"
                onClick={() => !disabled && setSelectedId(candidate.id)}
              >
                <div className="flex items-start justify-between">
                  <div>
                    <h4 className="text-[#E2E8F0] font-medium">{candidate.name}</h4>
                    <p className="text-sm text-[#94A3B8]">{candidate.title}</p>
                  </div>
                  <div className="text-right">
                    <div className={`font-mono text-sm ${overBudget ? 'text-amber-400' : 'text-[#E2E8F0]'}`}>
                      ${candidate.salary.toLocaleString()}
                    </div>
                    {overBudget && (
                      <div className="flex items-center gap-1 text-xs text-amber-400 mt-1">
                        <AlertTriangle className="w-3 h-3" /> Over budget
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex flex-wrap gap-2 mt-2">
                  {Object.entries(candidate.stats).map(([k, v]) => (
                    <span key={k} className="text-xs bg-[#0F1729] text-[#94A3B8] px-2 py-1 rounded">
                      {k}: <span className="text-[#E2E8F0]">{v}</span>
                    </span>
                  ))}
                </div>
              </div>

              <button
                onClick={(e) => { e.stopPropagation(); setExpandedId(isExpanded ? null : candidate.id) }}
                className="w-full px-4 py-2 text-xs text-[#94A3B8] hover:text-[#E2E8F0] flex items-center gap-1 border-t border-[#1E3A5F]/20"
              >
                {isExpanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                {isExpanded ? 'Less' : 'More details'}
              </button>

              {isExpanded && (
                <div className="px-4 pb-4 space-y-2 text-sm">
                  <p className="text-[#94A3B8]">{candidate.bio}</p>
                  <div className="flex gap-4">
                    <div>
                      <p className="text-xs text-green-400 mb-1">Strengths</p>
                      {candidate.strengths.map((s, i) => <p key={i} className="text-xs text-[#94A3B8]">+ {s}</p>)}
                    </div>
                    <div>
                      <p className="text-xs text-red-400 mb-1">Weaknesses</p>
                      {candidate.weaknesses.map((w, i) => <p key={i} className="text-xs text-[#94A3B8]">- {w}</p>)}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>

      <Button
        onClick={() => selectedId && onSubmit({ input: { selected: selectedId } })}
        disabled={!selectedId || disabled}
        className="w-full"
      >
        Confirm Selection
      </Button>
    </div>
  )
}
