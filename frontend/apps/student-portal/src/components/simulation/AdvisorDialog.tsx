'use client'

import { useState, useEffect } from 'react'
import { User, AlertTriangle, Sparkles, Frown, Award } from 'lucide-react'
import { LottieCharacter } from './LottieCharacter'

interface AdvisorDialogProps {
  mood: string
  text: string
  advisorName: string
  characterId?: string
  onDismiss: () => void
  autoAdvance?: number
}

const moodIcons: Record<string, any> = {
  neutral: User,
  concerned: AlertTriangle,
  excited: Sparkles,
  disappointed: Frown,
  proud: Award,
}

const moodColors: Record<string, string> = {
  neutral: 'text-[#94A3B8] bg-[#94A3B8]/10',
  concerned: 'text-amber-400 bg-amber-400/10',
  excited: 'text-[#0891B2] bg-[#0891B2]/10',
  disappointed: 'text-red-400 bg-red-400/10',
  proud: 'text-[#F97316] bg-[#F97316]/10',
}

export function AdvisorDialog({ mood, text, advisorName, characterId, onDismiss, autoAdvance }: AdvisorDialogProps) {
  const [displayedText, setDisplayedText] = useState('')
  const [isComplete, setIsComplete] = useState(false)

  useEffect(() => {
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (prefersReduced) {
      setDisplayedText(text)
      setIsComplete(true)
      return
    }

    let i = 0
    const interval = setInterval(() => {
      i++
      setDisplayedText(text.slice(0, i))
      if (i >= text.length) {
        clearInterval(interval)
        setIsComplete(true)
      }
    }, 16)
    return () => clearInterval(interval)
  }, [text])

  useEffect(() => {
    if (autoAdvance && isComplete) {
      const timeout = setTimeout(onDismiss, autoAdvance)
      return () => clearTimeout(timeout)
    }
  }, [autoAdvance, isComplete, onDismiss])

  const Icon = moodIcons[mood] || User
  const colorClass = moodColors[mood] || moodColors.neutral

  return (
    <div className="bg-[#1A2744] border border-[#1E3A5F] rounded-xl p-4">
      <div className="flex gap-3">
        {characterId ? (
          <LottieCharacter characterId={characterId} mood={mood} size={56} />
        ) : (
          <div className={`w-12 h-12 rounded-full ${colorClass} flex items-center justify-center shrink-0`}>
            <Icon className="w-6 h-6" />
          </div>
        )}
        <div className="flex-1 min-w-0">
          <p className="text-xs text-[#94A3B8] mb-1">{advisorName}</p>
          <p className="text-sm text-[#E2E8F0] leading-relaxed">
            {displayedText}
            {!isComplete && <span className="inline-block w-0.5 h-4 bg-[#0891B2] ml-0.5 animate-pulse" />}
          </p>
        </div>
        {isComplete && (
          <button
            onClick={onDismiss}
            className="text-[#94A3B8] hover:text-[#E2E8F0] animate-bounce text-lg shrink-0 self-end cursor-pointer transition-colors"
            aria-label="Continue"
          >
            ▼
          </button>
        )}
      </div>
      {isComplete && !autoAdvance && (
        <p className="text-xs text-[#94A3B8]/50 text-center mt-2">Click ▼ to continue</p>
      )}
    </div>
  )
}
