'use client'

import { useState } from 'react'
import { CheckCircle2, Circle, Users } from 'lucide-react'
import { LottieCharacter } from './LottieCharacter'

interface Stakeholder {
  id: string
  name: string
  role: string
  teaser: string
  revealedInfo: string
}

interface StakeholderConfig {
  maxSelections: number
  instruction: string
  stakeholders: Stakeholder[]
}

const priorityColors: Record<string, string> = {
  high: 'text-emerald-400 bg-emerald-400/10 border-emerald-400/20',
  medium: 'text-amber-400 bg-amber-400/10 border-amber-400/20',
  low: 'text-slate-400 bg-slate-400/10 border-slate-400/20',
}

interface StakeholderMeetingInputProps {
  config: StakeholderConfig
  onSubmit: (data: { input: { selectedStakeholders: string[] } }) => void
  disabled?: boolean
  stakeholderHints?: Record<string, { hint: string; priority: 'high' | 'medium' | 'low' }>
}

export function StakeholderMeetingInput({ config, onSubmit, disabled, stakeholderHints }: StakeholderMeetingInputProps) {
  const [selected, setSelected] = useState<string[]>([])
  const [phase, setPhase] = useState<'select' | 'reveal'>('select')
  const [revealIndex, setRevealIndex] = useState(0)

  const toggleStakeholder = (id: string) => {
    if (disabled) return
    setSelected(prev => {
      if (prev.includes(id)) return prev.filter(s => s !== id)
      if (prev.length >= config.maxSelections) return prev
      return [...prev, id]
    })
  }

  const handleProceed = () => {
    setPhase('reveal')
    setRevealIndex(0)
  }

  const handleContinue = () => {
    onSubmit({ input: { selectedStakeholders: selected } })
  }

  const selectedStakeholders = config.stakeholders.filter(s => selected.includes(s.id))

  if (phase === 'reveal') {
    return (
      <div className="bg-[#222a3d] rounded-xl p-5 space-y-4">
        <div className="flex items-center gap-2">
          <Users className="w-4 h-4 text-slate-400" />
          <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">Meeting Notes</h3>
        </div>

        {selectedStakeholders.map((s, i) => (
          <div
            key={s.id}
            className={`bg-[#1A2744] border border-white/5 rounded-xl p-4 transition-all duration-500 ${
              i <= revealIndex ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'
            }`}
            style={{ transitionDelay: `${i * 300}ms` }}
          >
            <div className="flex items-center gap-2 mb-2">
              {(s as any).characterId ? (
                <LottieCharacter characterId={(s as any).characterId} mood="neutral" size={40} />
              ) : (
                <div className="w-8 h-8 rounded-full bg-[#6cd3f7]/10 flex items-center justify-center text-[#6cd3f7] text-sm font-bold">
                  {s.name.charAt(0)}
                </div>
              )}
              <div>
                <p className="text-sm font-medium text-[#dbe2fb]">{s.name}</p>
                <p className="text-xs text-slate-400">{s.role}</p>
              </div>
            </div>
            <p className="text-sm text-[#dbe2fb] leading-relaxed">{s.revealedInfo}</p>
          </div>
        ))}

        {revealIndex < selectedStakeholders.length - 1 ? (
          <button
            type="button"
            onClick={() => setRevealIndex(prev => prev + 1)}
            className="w-full px-10 py-4 border border-[#6cd3f7]/30 text-[#6cd3f7] font-bold uppercase tracking-widest text-sm hover:bg-[#6cd3f7]/10 transition-colors rounded-lg"
          >
            Next Meeting
          </button>
        ) : (
          <button
            type="button"
            onClick={handleContinue}
            className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] rounded-lg"
          >
            Continue
          </button>
        )}
      </div>
    )
  }

  return (
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-4">
      <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
        Stakeholder Meeting
      </h3>
      <p className="text-sm text-[#bdc8ce]">{config.instruction}</p>
      <div className="text-xs uppercase tracking-widest text-[#6cd3f7] font-bold">Selected: {selected.length}/{config.maxSelections}</div>

      <div className="space-y-3">
        {config.stakeholders.map((s) => {
          const isSelected = selected.includes(s.id)
          const isFull = selected.length >= config.maxSelections && !isSelected

          return (
            <div
              key={s.id}
              className={`bg-[#1A2744] border rounded-xl p-4 cursor-pointer transition-all ${
                isSelected ? 'border-[#6cd3f7]/50 ring-1 ring-[#6cd3f7]/30' : isFull ? 'border-white/5 opacity-50' : 'border-white/5 hover:border-[#6cd3f7]/30'
              } ${disabled ? 'opacity-60 pointer-events-none' : ''}`}
              onClick={() => toggleStakeholder(s.id)}
            >
              <div className="flex items-start gap-3">
                {isSelected ? (
                  <CheckCircle2 className="w-5 h-5 text-[#6cd3f7] mt-0.5 shrink-0" />
                ) : (
                  <Circle className="w-5 h-5 text-slate-500 mt-0.5 shrink-0" />
                )}
                <div className="flex-1">
                  <p className="text-sm font-medium text-[#dbe2fb]">{s.name}</p>
                  <p className="text-xs text-slate-400">{s.role}</p>
                  <p className="text-sm text-[#bdc8ce] mt-1">{s.teaser}</p>
                  {stakeholderHints?.[s.id] && (
                    <div className="mt-2 flex items-start gap-2">
                      <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] uppercase tracking-wider font-bold border ${priorityColors[stakeholderHints[s.id].priority]}`}>
                        {stakeholderHints[s.id].priority}
                      </span>
                      <p className="text-xs text-slate-400 leading-relaxed">{stakeholderHints[s.id].hint}</p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>

      <button
        type="button"
        onClick={handleProceed}
        disabled={selected.length === 0 || disabled}
        className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
      >
        Proceed to Meetings
      </button>
    </div>
  )
}
