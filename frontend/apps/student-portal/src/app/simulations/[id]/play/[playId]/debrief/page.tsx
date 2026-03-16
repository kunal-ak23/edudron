'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { simulationsApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Loader2, RotateCcw, History, ArrowLeft } from 'lucide-react'
import type { SimulationStateDTO } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

function scoreColor(score: number): string {
  if (score <= 30) return 'text-red-500'
  if (score <= 60) return 'text-yellow-500'
  if (score <= 80) return 'text-blue-500'
  return 'text-green-500'
}

function scoreBg(score: number): string {
  if (score <= 30) return 'bg-red-50 border-red-200'
  if (score <= 60) return 'bg-yellow-50 border-yellow-200'
  if (score <= 80) return 'bg-blue-50 border-blue-200'
  return 'bg-green-50 border-green-200'
}

export default function DebriefPage() {
  const params = useParams()
  const router = useRouter()
  const simulationId = params.id as string
  const playId = params.playId as string

  const [state, setState] = useState<SimulationStateDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [startingPlay, setStartingPlay] = useState(false)

  // Sequential reveal state
  const [visibleSections, setVisibleSections] = useState(0)

  useEffect(() => {
    loadDebrief()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playId])

  // Sequentially reveal debrief sections
  useEffect(() => {
    if (!state?.debrief) return

    const totalSections = 4
    if (visibleSections >= totalSections) return

    const timer = setTimeout(() => {
      setVisibleSections((v) => v + 1)
    }, 500)

    return () => clearTimeout(timer)
  }, [state, visibleSections])

  async function loadDebrief() {
    try {
      setLoading(true)
      setError(null)
      const data = await simulationsApi.getCurrentState(playId)
      if (data.phase !== 'DEBRIEF') {
        // Not in debrief phase, redirect back to play
        router.push(`/simulations/${simulationId}/play/${playId}`)
        return
      }
      setState(data)
      setVisibleSections(0)
      setTimeout(() => setVisibleSections(1), 300)
    } catch {
      setError('Failed to load debrief. The simulation may still be in progress.')
    } finally {
      setLoading(false)
    }
  }

  async function handlePlayAgain() {
    try {
      setStartingPlay(true)
      const play = await simulationsApi.startPlay(simulationId)
      router.push(`/simulations/${simulationId}/play/${play.id}`)
    } catch {
      setError('Failed to start a new play.')
      setStartingPlay(false)
    }
  }

  const score = state?.cumulativeScore ?? 0
  const debrief = state?.debrief

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50 flex flex-col">
        {/* Minimal top bar */}
        <div className="flex items-center justify-between px-4 py-3 bg-white border-b border-gray-200">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => router.push('/simulations')}
            className="text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4 mr-1" />
            Simulations
          </Button>
          <div className="text-sm text-gray-400 font-medium">Debrief</div>
          <div className="w-20" />
        </div>

        {/* Main content */}
        <div className="flex-1 flex items-start justify-center px-4 py-8 md:py-12">
          <div className="w-full max-w-2xl">
            {error && (
              <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                {error}
              </div>
            )}

            {loading ? (
              <div className="flex flex-col items-center justify-center py-20 gap-4">
                <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
                <p className="text-sm text-gray-500">Loading your debrief...</p>
              </div>
            ) : state && debrief ? (
              <div className="space-y-8">
                {/* Header with score + role + years */}
                <div className="text-center space-y-4">
                  <h1 className="text-3xl font-bold text-gray-900">
                    Simulation Complete
                  </h1>
                  <div className="flex items-center justify-center gap-4 flex-wrap">
                    <div className={`inline-flex items-center gap-3 px-6 py-4 rounded-2xl border ${scoreBg(score)}`}>
                      <span className="text-sm font-medium text-gray-600 uppercase tracking-wide">
                        Score
                      </span>
                      <span className={`text-5xl font-bold tabular-nums ${scoreColor(score)}`}>
                        {score}
                      </span>
                      <span className="text-sm text-gray-400">pts</span>
                    </div>
                  </div>
                  <div className="flex items-center justify-center gap-4 text-sm text-gray-500">
                    {state.currentRole && (
                      <span>Final Role: <span className="font-medium text-gray-700">{state.currentRole}</span></span>
                    )}
                    <span>Years Completed: <span className="font-medium text-gray-700">{state.currentYear}</span></span>
                  </div>
                </div>

                {/* Section 1: Your Path */}
                <div
                  className={`transition-all duration-500 ${
                    visibleSections >= 1
                      ? 'opacity-100 translate-y-0'
                      : 'opacity-0 translate-y-4'
                  }`}
                >
                  <div className="border border-gray-200 rounded-xl p-6 bg-white">
                    <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">
                      Your Path
                    </h2>
                    <p className="text-gray-800 leading-relaxed whitespace-pre-line">
                      {debrief.yourPath}
                    </p>
                  </div>
                </div>

                {/* Section 2: The Concept at Work */}
                <div
                  className={`transition-all duration-500 ${
                    visibleSections >= 2
                      ? 'opacity-100 translate-y-0'
                      : 'opacity-0 translate-y-4'
                  }`}
                >
                  <div className="border border-blue-200 rounded-xl p-6 bg-blue-50/50">
                    <h2 className="text-sm font-semibold text-blue-600 uppercase tracking-wide mb-3">
                      The Concept at Work
                    </h2>
                    <p className="text-gray-800 leading-relaxed whitespace-pre-line">
                      {debrief.conceptAtWork}
                    </p>
                  </div>
                </div>

                {/* Section 3: The Gap */}
                <div
                  className={`transition-all duration-500 ${
                    visibleSections >= 3
                      ? 'opacity-100 translate-y-0'
                      : 'opacity-0 translate-y-4'
                  }`}
                >
                  <div className="border border-amber-200 rounded-xl p-6 bg-amber-50/50">
                    <h2 className="text-sm font-semibold text-amber-600 uppercase tracking-wide mb-3">
                      The Gap
                    </h2>
                    <p className="text-gray-800 leading-relaxed whitespace-pre-line italic">
                      {debrief.theGap}
                    </p>
                  </div>
                </div>

                {/* Section 4: Play Again */}
                <div
                  className={`transition-all duration-500 ${
                    visibleSections >= 4
                      ? 'opacity-100 translate-y-0'
                      : 'opacity-0 translate-y-4'
                  }`}
                >
                  <div className="border border-gray-200 rounded-xl p-6 bg-white text-center space-y-4">
                    <p className="text-gray-700 leading-relaxed whitespace-pre-line">
                      {debrief.playAgain}
                    </p>
                    <Button
                      size="lg"
                      onClick={handlePlayAgain}
                      disabled={startingPlay}
                      className="px-8"
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

                {/* Navigation links */}
                <div
                  className={`transition-all duration-500 delay-300 ${
                    visibleSections >= 4
                      ? 'opacity-100 translate-y-0'
                      : 'opacity-0 translate-y-4'
                  }`}
                >
                  <div className="flex items-center justify-center gap-6 pt-2 pb-8">
                    <button
                      onClick={() =>
                        router.push(`/simulations/${simulationId}/history`)
                      }
                      className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors"
                    >
                      <History className="h-4 w-4" />
                      View History
                    </button>
                    <button
                      onClick={() => router.push('/simulations')}
                      className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors"
                    >
                      <ArrowLeft className="h-4 w-4" />
                      Back to Simulations
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-center py-20">
                <p className="text-gray-500 mb-4">
                  Could not load the debrief.
                </p>
                <Button onClick={loadDebrief}>Retry</Button>
              </div>
            )}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  )
}
