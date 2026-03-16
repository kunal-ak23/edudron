'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Loader2, RotateCcw } from 'lucide-react'
import { simulationsApi } from '@/lib/api'
import type { DebriefDTO } from '@kunal-ak23/edudron-shared-utils'

interface FiredScreenProps {
  simulationId: string
  cumulativeScore: number
  debrief?: DebriefDTO
}

export function FiredScreen({ simulationId, cumulativeScore, debrief }: FiredScreenProps) {
  const router = useRouter()
  const [startingPlay, setStartingPlay] = useState(false)

  async function handlePlayAgain() {
    try {
      setStartingPlay(true)
      const play = await simulationsApi.startPlay(simulationId)
      router.push(`/simulations/${simulationId}/play/${play.id}`)
    } catch {
      setStartingPlay(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-900 flex flex-col items-center justify-center px-4 text-center">
      <div className="w-full max-w-lg space-y-8">
        {/* Heading */}
        <div className="space-y-3">
          <h1 className="text-4xl md:text-5xl font-bold text-red-400">Your Tenure Has Ended</h1>
          <p className="text-gray-400 text-lg">The board has decided to move in a different direction.</p>
        </div>

        {/* Score */}
        <div className="inline-flex items-baseline gap-2 px-6 py-4 rounded-2xl bg-gray-800 border border-gray-700">
          <span className="text-sm font-medium text-gray-400 uppercase tracking-wide">Final Score</span>
          <span className="text-5xl font-bold text-red-400 tabular-nums">{cumulativeScore}</span>
          <span className="text-sm text-gray-500">pts</span>
        </div>

        {/* Debrief sections */}
        {debrief && (
          <div className="text-left space-y-4">
            {debrief.yourPath && (
              <div className="p-4 rounded-xl bg-gray-800 border border-gray-700">
                <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wide mb-2">Your Path</h2>
                <p className="text-gray-300 leading-relaxed whitespace-pre-line">{debrief.yourPath}</p>
              </div>
            )}
            {debrief.conceptAtWork && (
              <div className="p-4 rounded-xl bg-gray-800 border border-gray-700">
                <h2 className="text-sm font-semibold text-blue-400 uppercase tracking-wide mb-2">The Concept at Work</h2>
                <p className="text-gray-300 leading-relaxed whitespace-pre-line">{debrief.conceptAtWork}</p>
              </div>
            )}
            {debrief.theGap && (
              <div className="p-4 rounded-xl bg-gray-800 border border-gray-700">
                <h2 className="text-sm font-semibold text-amber-400 uppercase tracking-wide mb-2">The Gap</h2>
                <p className="text-gray-300 leading-relaxed whitespace-pre-line italic">{debrief.theGap}</p>
              </div>
            )}
            {debrief.playAgain && (
              <div className="p-4 rounded-xl bg-gray-800 border border-gray-700">
                <p className="text-gray-300 leading-relaxed whitespace-pre-line">{debrief.playAgain}</p>
              </div>
            )}
          </div>
        )}

        {/* Play Again */}
        <div className="pt-4 pb-8">
          <Button
            size="lg"
            variant="outline"
            onClick={handlePlayAgain}
            disabled={startingPlay}
            className="px-8 border-gray-600 text-gray-200 hover:bg-gray-800 hover:text-white"
          >
            {startingPlay ? (
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
            ) : (
              <RotateCcw className="h-4 w-4 mr-2" />
            )}
            Play Again
          </Button>
        </div>
      </div>
    </div>
  )
}
