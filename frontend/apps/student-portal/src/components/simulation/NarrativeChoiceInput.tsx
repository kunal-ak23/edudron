'use client'

import { useState } from 'react'
import type { ChoiceDTO } from '@kunal-ak23/edudron-shared-utils'

interface NarrativeChoiceInputProps {
  choices: ChoiceDTO[]
  onSubmit: (data: { choiceId: string }) => void
  disabled?: boolean
}

export function NarrativeChoiceInput({ choices, onSubmit, disabled }: NarrativeChoiceInputProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null)

  return (
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-3">
      <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
        Choose Your Action
      </h3>

      {choices.map((choice) => (
        <button
          key={choice.id}
          type="button"
          className={`w-full text-left rounded-xl p-4 border transition-all duration-200 ${
            selectedId === choice.id
              ? 'bg-[#1A2744] border-[#6cd3f7]/50 ring-1 ring-[#6cd3f7]/30'
              : 'bg-[#1A2744] border-white/5 hover:border-[#6cd3f7]/30'
          } ${disabled ? 'opacity-60 pointer-events-none' : 'cursor-pointer'}`}
          onClick={() => !disabled && setSelectedId(choice.id)}
        >
          <p className="text-sm text-[#dbe2fb] leading-relaxed">{choice.text}</p>
        </button>
      ))}

      <div className="pt-1">
        <button
          type="button"
          onClick={() => selectedId && onSubmit({ choiceId: selectedId })}
          disabled={!selectedId || disabled}
          className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
        >
          Confirm Decision
        </button>
      </div>
    </div>
  )
}
