'use client'

import { useState } from 'react'
import { ChevronDown, ChevronUp, AlertTriangle } from 'lucide-react'
import { LottieCharacter } from './LottieCharacter'

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
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
          {config.action === 'hire' ? 'Hire Decision' : 'Termination Decision'}
        </h3>
        <span className="text-xs uppercase tracking-widest text-[#6cd3f7] font-bold">
          Budget: ${config.budgetLimit.toLocaleString()}
        </span>
      </div>
      <p className="text-sm text-[#bdc8ce]">
        {config.action === 'hire' ? 'Select a candidate to hire' : 'Select a team member to let go'}
      </p>

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
                  <div className="flex items-center gap-3">
                    {(candidate as any).characterId && (
                      <LottieCharacter characterId={(candidate as any).characterId} mood="neutral" size={40} />
                    )}
                    <div>
                    <h4 className="text-[#dbe2fb] font-medium">{candidate.name}</h4>
                    <p className="text-sm text-slate-400">{candidate.title}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <div className={`font-mono text-sm ${overBudget ? 'text-amber-400' : 'text-[#dbe2fb]'}`}>
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
                    <span key={k} className="text-xs bg-[#0F1729] text-slate-400 px-2 py-1 rounded">
                      {k}: <span className="text-[#dbe2fb]">{v}</span>
                    </span>
                  ))}
                </div>
              </div>

              <button
                onClick={(e) => { e.stopPropagation(); setExpandedId(isExpanded ? null : candidate.id) }}
                className="w-full px-4 py-2 text-xs text-slate-400 hover:text-[#dbe2fb] flex items-center gap-1 border-t border-[#1E3A5F]/20"
              >
                {isExpanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                {isExpanded ? 'Less' : 'More details'}
              </button>

              {isExpanded && (
                <div className="px-4 pb-4 space-y-2 text-sm">
                  <p className="text-slate-400">{candidate.bio}</p>
                  <div className="flex gap-4">
                    <div>
                      <p className="text-xs text-green-400 mb-1">Strengths</p>
                      {candidate.strengths.map((s, i) => <p key={i} className="text-xs text-slate-400">+ {s}</p>)}
                    </div>
                    <div>
                      <p className="text-xs text-red-400 mb-1">Weaknesses</p>
                      {candidate.weaknesses.map((w, i) => <p key={i} className="text-xs text-slate-400">- {w}</p>)}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>

      <button
        type="button"
        onClick={() => selectedId && onSubmit({ input: { selected: selectedId } })}
        disabled={!selectedId || disabled}
        className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
      >
        Confirm Selection
      </button>
    </div>
  )
}
