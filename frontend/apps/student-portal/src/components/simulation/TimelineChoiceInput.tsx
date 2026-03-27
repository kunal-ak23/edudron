'use client'

import { useState } from 'react'

interface TimelineConfig {
  milestones: Array<{ id: string; label: string; description?: string }>
}

interface TimelineChoiceInputProps {
  config: TimelineConfig
  onSubmit: (data: { input: { selected: string } }) => void
  disabled?: boolean
}

export function TimelineChoiceInput({ config, onSubmit, disabled }: TimelineChoiceInputProps) {
  const milestones = config.milestones ?? []
  const [selectedId, setSelectedId] = useState<string | null>(null)

  return (
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-5">
      <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
        Timeline Decision
      </h3>

      {/* Horizontal timeline */}
      <div className="relative">
        {/* Line connecting milestones */}
        <div className="absolute top-4 left-4 right-4 h-0.5 bg-[#1E3A5F]/50" />

        <div className="relative flex justify-between">
          {milestones.map((milestone) => {
            const isSelected = selectedId === milestone.id
            return (
              <button
                key={milestone.id}
                type="button"
                onClick={() => !disabled && setSelectedId(milestone.id)}
                disabled={disabled}
                className="flex flex-col items-center gap-2 relative z-10 group"
              >
                <div
                  className={`w-8 h-8 rounded-full border-2 flex items-center justify-center transition-all duration-200 ${
                    isSelected
                      ? 'border-[#6cd3f7] bg-[#6cd3f7]/20 scale-110'
                      : 'border-[#1E3A5F] bg-[#1A2744] group-hover:border-[#6cd3f7]/50'
                  } ${disabled ? 'cursor-not-allowed' : 'cursor-pointer'}`}
                >
                  <div className={`w-2.5 h-2.5 rounded-full ${
                    isSelected ? 'bg-[#6cd3f7]' : 'bg-[#1E3A5F] group-hover:bg-[#6cd3f7]/50'
                  }`} />
                </div>
                <span className={`text-xs text-center max-w-[80px] leading-tight ${
                  isSelected ? 'font-bold text-[#6cd3f7]' : 'text-slate-400'
                }`}>
                  {milestone.label}
                </span>
                {milestone.description && isSelected && (
                  <span className="text-xs text-[#bdc8ce] text-center max-w-[100px]">
                    {milestone.description}
                  </span>
                )}
              </button>
            )
          })}
        </div>
      </div>

      <div className="pt-1">
        <button
          type="button"
          onClick={() => selectedId && onSubmit({ input: { selected: selectedId } })}
          disabled={!selectedId || disabled}
          className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
        >
          Confirm
        </button>
      </div>
    </div>
  )
}
