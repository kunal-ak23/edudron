'use client'

import { useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import type { ChoiceDTO } from '@kunal-ak23/edudron-shared-utils'

interface NarrativeChoiceInputProps {
  choices: ChoiceDTO[]
  onSubmit: (data: { choiceId: string }) => void
  disabled?: boolean
}

export function NarrativeChoiceInput({ choices, onSubmit, disabled }: NarrativeChoiceInputProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null)

  return (
    <div className="space-y-3">
      {choices.map((choice) => (
        <Card
          key={choice.id}
          className={`cursor-pointer transition-all duration-200 ${
            selectedId === choice.id
              ? 'ring-2 ring-primary-600 border-primary-600'
              : 'hover:border-primary-400 hover:shadow-sm'
          } ${disabled ? 'opacity-60 pointer-events-none' : ''}`}
          onClick={() => !disabled && setSelectedId(choice.id)}
        >
          <CardContent className="p-4">
            <p className="text-sm leading-relaxed">{choice.text}</p>
          </CardContent>
        </Card>
      ))}

      <div className="pt-2">
        <Button
          onClick={() => selectedId && onSubmit({ choiceId: selectedId })}
          disabled={!selectedId || disabled}
          className="w-full"
        >
          Confirm Decision
        </Button>
      </div>
    </div>
  )
}
