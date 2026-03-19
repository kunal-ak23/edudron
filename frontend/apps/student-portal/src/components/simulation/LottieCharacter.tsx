'use client'

import { getCharacter } from '@/lib/character-registry'

interface LottieCharacterProps {
  characterId: string
  mood: string
  size?: number
  className?: string
}

export function LottieCharacter({ characterId, mood, size = 64, className }: LottieCharacterProps) {
  const character = getCharacter(characterId)
  const emoji = character.moods[mood] || character.moods['neutral'] || '😊'
  const fontSize = Math.round(size * 0.5)

  return (
    <div
      className={`rounded-full flex items-center justify-center shrink-0 transition-all duration-300 ${className || ''}`}
      style={{
        width: size,
        height: size,
        backgroundColor: character.color + '20',
        border: `2px solid ${character.color}40`,
      }}
    >
      <span style={{ fontSize }} role="img" aria-label={`${character.name} - ${mood}`}>
        {emoji}
      </span>
    </div>
  )
}
