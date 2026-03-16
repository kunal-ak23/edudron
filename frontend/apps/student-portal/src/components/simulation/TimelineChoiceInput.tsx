'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'

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
    <div className="space-y-4">
      {/* Horizontal timeline */}
      <div className="relative">
        {/* Line connecting milestones */}
        <div className="absolute top-4 left-4 right-4 h-0.5 bg-gray-200" />

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
                      ? 'border-primary-600 bg-primary-600 text-white scale-110'
                      : 'border-gray-300 bg-white text-gray-400 group-hover:border-primary-400'
                  } ${disabled ? 'cursor-not-allowed' : 'cursor-pointer'}`}
                >
                  <div className={`w-2.5 h-2.5 rounded-full ${
                    isSelected ? 'bg-white' : 'bg-gray-300 group-hover:bg-primary-300'
                  }`} />
                </div>
                <span className={`text-xs text-center max-w-[80px] leading-tight ${
                  isSelected ? 'font-semibold text-primary-700' : 'text-gray-600'
                }`}>
                  {milestone.label}
                </span>
                {milestone.description && isSelected && (
                  <span className="text-xs text-gray-500 text-center max-w-[100px]">
                    {milestone.description}
                  </span>
                )}
              </button>
            )
          })}
        </div>
      </div>

      <div className="pt-2">
        <Button
          onClick={() => selectedId && onSubmit({ input: { selected: selectedId } })}
          disabled={!selectedId || disabled}
          className="w-full"
        >
          Confirm
        </Button>
      </div>
    </div>
  )
}
