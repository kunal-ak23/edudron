'use client'

import { useState, useEffect, useRef } from 'react'
import Lottie from 'lottie-react'
import { getCharacter } from '@/lib/character-registry'
import { User } from 'lucide-react'

const lottieCache = new Map<string, any>()

interface LottieCharacterProps {
  characterId: string
  mood: string
  size?: number
  className?: string
}

export function LottieCharacter({ characterId, mood, size = 64, className }: LottieCharacterProps) {
  const [animationData, setAnimationData] = useState<any>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    const character = getCharacter(characterId)
    const path = character.moods[mood] || character.moods['neutral']
    if (!path) {
      setError(true)
      return
    }

    if (lottieCache.has(path)) {
      setAnimationData(lottieCache.get(path))
      setError(false)
      return
    }

    fetch(path)
      .then(res => {
        if (!res.ok) throw new Error('Failed to load')
        return res.json()
      })
      .then(data => {
        lottieCache.set(path, data)
        setAnimationData(data)
        setError(false)
      })
      .catch(() => {
        setError(true)
      })
  }, [characterId, mood])

  if (error || !animationData) {
    return (
      <div
        className={`rounded-full bg-[#94A3B8]/10 flex items-center justify-center shrink-0 ${className || ''}`}
        style={{ width: size, height: size }}
      >
        <User className="text-[#94A3B8]" style={{ width: size * 0.5, height: size * 0.5 }} />
      </div>
    )
  }

  return (
    <div className={`shrink-0 ${className || ''}`} style={{ width: size, height: size }}>
      <Lottie
        animationData={animationData}
        loop
        autoplay
        style={{ width: size, height: size }}
      />
    </div>
  )
}
