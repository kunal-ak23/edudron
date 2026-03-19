'use client'

import { useState, useEffect, useRef, useCallback } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { AlertTriangle, Clock } from 'lucide-react'
import type { ChoiceDTO } from '@kunal-ak23/edudron-shared-utils'

interface CrisisConfig {
  timeLimit: number
  crisisTitle: string
  crisisDescription: string
  severity: 'critical' | 'high' | 'medium'
  defaultOnExpiry: string
}

interface CrisisResponseInputProps {
  config: CrisisConfig
  choices?: ChoiceDTO[]
  onSubmit: (data: { choiceId?: string; input?: Record<string, any> }) => void
  disabled?: boolean
}

export function CrisisResponseInput({ config, choices = [], onSubmit, disabled }: CrisisResponseInputProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [timeLeft, setTimeLeft] = useState(config.timeLimit)
  const [expired, setExpired] = useState(false)
  const submittedRef = useRef(false)

  const handleExpiry = useCallback(() => {
    if (submittedRef.current) return
    submittedRef.current = true
    setExpired(true)
    onSubmit({ input: { expired: true }, choiceId: config.defaultOnExpiry })
  }, [config.defaultOnExpiry, onSubmit])

  useEffect(() => {
    if (disabled || expired) return
    const interval = setInterval(() => {
      setTimeLeft(prev => {
        if (prev <= 1) {
          clearInterval(interval)
          handleExpiry()
          return 0
        }
        return prev - 1
      })
    }, 1000)
    return () => clearInterval(interval)
  }, [disabled, expired, handleExpiry])

  const handleSubmit = () => {
    if (!selectedId || submittedRef.current) return
    submittedRef.current = true
    onSubmit({ choiceId: selectedId })
  }

  const severityColors = {
    critical: { bg: 'bg-red-900/30', border: 'border-red-500/50', text: 'text-red-400' },
    high: { bg: 'bg-amber-900/30', border: 'border-amber-500/50', text: 'text-amber-400' },
    medium: { bg: 'bg-yellow-900/30', border: 'border-yellow-500/50', text: 'text-yellow-400' },
  }
  const colors = severityColors[config.severity] || severityColors.high

  return (
    <div className="space-y-4">
      {/* Alert Banner */}
      <div className={`${colors.bg} ${colors.border} border rounded-lg p-4`}>
        <div className="flex items-center gap-2 mb-2">
          <AlertTriangle className={`w-5 h-5 ${colors.text}`} />
          <h3 className={`font-medium ${colors.text}`}>{config.crisisTitle}</h3>
        </div>
        <p className="text-sm text-[#E2E8F0]">{config.crisisDescription}</p>
      </div>

      {/* Timer */}
      <div className="flex justify-center">
        <div className={`flex items-center gap-2 px-6 py-3 rounded-full ${
          timeLeft <= 10 ? 'bg-red-900/30 animate-pulse' : 'bg-[#1A2744]'
        } border border-[#1E3A5F]/30`}>
          <Clock className={`w-5 h-5 ${timeLeft <= 10 ? 'text-red-400' : 'text-[#0891B2]'}`} />
          <span className={`text-2xl font-mono ${timeLeft <= 10 ? 'text-red-400' : 'text-[#E2E8F0]'}`}>
            {timeLeft}s
          </span>
        </div>
      </div>

      {/* Choices */}
      {!expired && (
        <>
          <div className="space-y-3">
            {choices.map((choice) => (
              <Card
                key={choice.id}
                className={`cursor-pointer transition-all bg-[#1A2744] border-[#1E3A5F]/30 ${
                  selectedId === choice.id ? 'ring-2 ring-[#0891B2] border-[#0891B2]' : 'hover:border-[#0891B2]/50'
                } ${disabled ? 'opacity-60 pointer-events-none' : ''}`}
                onClick={() => !disabled && !expired && setSelectedId(choice.id)}
              >
                <CardContent className="p-4">
                  <p className="text-sm text-[#E2E8F0]">{choice.text}</p>
                </CardContent>
              </Card>
            ))}
          </div>

          <Button onClick={handleSubmit} disabled={!selectedId || disabled || expired} className="w-full">
            Confirm Decision
          </Button>
        </>
      )}

      {expired && (
        <div className="text-center text-red-400 text-sm py-4">
          Time expired — default response applied.
        </div>
      )}
    </div>
  )
}
