'use client'

import { useState } from 'react'
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
  onSubmit: (data: { input: Record<string, any> }) => void
  disabled?: boolean
}

export function CompoundInput({ config, onSubmit, disabled }: CompoundInputProps) {
  const steps = config.steps ?? []
  const [currentStep, setCurrentStep] = useState(0)
  const [stepResults, setStepResults] = useState<any[]>([])

  const handleStepSubmit = (data: any) => {
    // Flatten: sub-components return { input: {...} } or { choiceId: 'x' }
    // Extract the input part so backend receives step1.marketing not step1.input.marketing
    const flatStep = data.input || data
    const newResults = [...stepResults, flatStep]
    setStepResults(newResults)

    if (currentStep < steps.length - 1) {
      setCurrentStep(currentStep + 1)
    }
  }

  const step = steps[currentStep]
  const allStepsComplete = stepResults.length >= steps.length

  if (!step && !allStepsComplete) {
    return <div className="text-sm text-slate-400">No steps configured.</div>
  }

  // Build dynamic step keys: step1, step2, step3, ...
  const buildPayload = () => {
    const input: Record<string, any> = {}
    stepResults.forEach((result, i) => {
      input[`step${i + 1}`] = result
    })
    return input
  }

  return (
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-4">
      {/* Step indicator */}
      <div className="flex items-center gap-2 text-sm mb-2">
        {steps.map((s, i) => (
          <div key={i} className="flex items-center gap-1">
            <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
              i < stepResults.length
                ? 'bg-green-400/20 text-green-400'
                : i === currentStep
                  ? 'bg-[#6cd3f7]/20 text-[#6cd3f7]'
                  : 'bg-white/5 text-slate-500'
            }`}>
              {i + 1}
            </span>
            <span className={`text-xs uppercase tracking-widest ${
              i === currentStep ? 'font-bold text-[#dbe2fb]' : 'text-slate-500'
            }`}>
              {s.label || `Step ${i + 1}`}
            </span>
            {i < steps.length - 1 && <span className="text-slate-600 mx-1">/</span>}
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
        <div className="pt-1">
          <button
            type="button"
            onClick={() => onSubmit({ input: buildPayload() })}
            disabled={disabled}
            className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
          >
            Submit Final Decision
          </button>
        </div>
      )}
    </div>
  )
}
