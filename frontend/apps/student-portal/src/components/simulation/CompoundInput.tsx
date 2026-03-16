'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { DecisionInput } from './DecisionInput'

interface CompoundConfig {
  steps: Array<{
    label: string
    decisionType: string
    decisionConfig?: any
    choices?: Array<{ id: string; text: string }>
  }>
}

interface CompoundInputProps {
  config: CompoundConfig
  onSubmit: (data: { input: { step1: any; step2: any } }) => void
  disabled?: boolean
}

export function CompoundInput({ config, onSubmit, disabled }: CompoundInputProps) {
  const steps = config.steps ?? []
  const [currentStep, setCurrentStep] = useState(0)
  const [stepResults, setStepResults] = useState<any[]>([])

  const handleStepSubmit = (data: any) => {
    const newResults = [...stepResults, data]
    setStepResults(newResults)

    if (currentStep < steps.length - 1) {
      setCurrentStep(currentStep + 1)
    }
  }

  const step = steps[currentStep]
  const allStepsComplete = stepResults.length >= steps.length

  if (!step && !allStepsComplete) {
    return <div className="text-sm text-gray-500">No steps configured.</div>
  }

  return (
    <div className="space-y-4">
      {/* Step indicator */}
      <div className="flex items-center gap-2 text-sm text-gray-500 mb-2">
        {steps.map((s, i) => (
          <div key={i} className="flex items-center gap-1">
            <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold ${
              i < stepResults.length
                ? 'bg-green-100 text-green-700'
                : i === currentStep
                  ? 'bg-primary-100 text-primary-700'
                  : 'bg-gray-100 text-gray-400'
            }`}>
              {i + 1}
            </span>
            <span className={i === currentStep ? 'font-medium text-gray-700' : ''}>
              {s.label || `Step ${i + 1}`}
            </span>
            {i < steps.length - 1 && <span className="text-gray-300 mx-1">/</span>}
          </div>
        ))}
      </div>

      {!allStepsComplete && step && (
        <DecisionInput
          decisionType={step.decisionType}
          decisionConfig={step.decisionConfig}
          choices={step.choices}
          onSubmit={handleStepSubmit}
          disabled={disabled}
        />
      )}

      {allStepsComplete && (
        <div className="pt-2">
          <Button
            onClick={() => onSubmit({ input: { step1: stepResults[0], step2: stepResults[1] } })}
            disabled={disabled}
            className="w-full"
          >
            Submit Final Decision
          </Button>
        </div>
      )}
    </div>
  )
}
