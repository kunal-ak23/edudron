'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { CheckCircle2, Circle, Users } from 'lucide-react'

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

interface StakeholderMeetingInputProps {
  config: StakeholderConfig
  onSubmit: (data: { input: { selectedStakeholders: string[] } }) => void
  disabled?: boolean
}

export function StakeholderMeetingInput({ config, onSubmit, disabled }: StakeholderMeetingInputProps) {
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
      <div className="space-y-4">
        <div className="flex items-center gap-2 text-sm text-[#94A3B8]">
          <Users className="w-4 h-4" />
          Meeting Notes
        </div>

        {selectedStakeholders.map((s, i) => (
          <div
            key={s.id}
            className={`bg-[#1A2744] border border-[#1E3A5F]/30 rounded-xl p-4 transition-all duration-500 ${
              i <= revealIndex ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'
            }`}
            style={{ transitionDelay: `${i * 300}ms` }}
          >
            <div className="flex items-center gap-2 mb-2">
              <div className="w-8 h-8 rounded-full bg-[#0891B2]/20 flex items-center justify-center text-[#0891B2] text-sm font-medium">
                {s.name.charAt(0)}
              </div>
              <div>
                <p className="text-sm font-medium text-[#E2E8F0]">{s.name}</p>
                <p className="text-xs text-[#94A3B8]">{s.role}</p>
              </div>
            </div>
            <p className="text-sm text-[#E2E8F0] leading-relaxed">{s.revealedInfo}</p>
          </div>
        ))}

        {revealIndex < selectedStakeholders.length - 1 ? (
          <Button onClick={() => setRevealIndex(prev => prev + 1)} className="w-full">
            Next Meeting
          </Button>
        ) : (
          <Button onClick={handleContinue} className="w-full">
            Continue
          </Button>
        )}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-[#94A3B8]">{config.instruction}</p>
      <div className="text-xs text-[#0891B2]">Selected: {selected.length}/{config.maxSelections}</div>

      <div className="space-y-3">
        {config.stakeholders.map((s) => {
          const isSelected = selected.includes(s.id)
          const isFull = selected.length >= config.maxSelections && !isSelected

          return (
            <div
              key={s.id}
              className={`bg-[#1A2744] border rounded-xl p-4 cursor-pointer transition-all ${
                isSelected ? 'border-[#0891B2] ring-1 ring-[#0891B2]' : isFull ? 'border-[#1E3A5F]/20 opacity-50' : 'border-[#1E3A5F]/30 hover:border-[#1E3A5F]'
              } ${disabled ? 'opacity-60 pointer-events-none' : ''}`}
              onClick={() => toggleStakeholder(s.id)}
            >
              <div className="flex items-start gap-3">
                {isSelected ? (
                  <CheckCircle2 className="w-5 h-5 text-[#0891B2] mt-0.5 shrink-0" />
                ) : (
                  <Circle className="w-5 h-5 text-[#1E3A5F] mt-0.5 shrink-0" />
                )}
                <div>
                  <p className="text-sm font-medium text-[#E2E8F0]">{s.name}</p>
                  <p className="text-xs text-[#94A3B8]">{s.role}</p>
                  <p className="text-sm text-[#94A3B8] mt-1">{s.teaser}</p>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      <Button
        onClick={handleProceed}
        disabled={selected.length === 0 || disabled}
        className="w-full"
      >
        Proceed to Meetings
      </Button>
    </div>
  )
}
