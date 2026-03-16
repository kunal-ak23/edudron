'use client'

import { useEffect, useRef, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { simulationsApi } from '@/lib/api'
import { TypewriterText, usePrefersReducedMotion } from '@/components/psych-test/TypewriterText'
import { DecisionInput } from '@/components/simulation/DecisionInput'
import { PlayHeader } from '@/components/simulation/PlayHeader'
import { YearEndReview } from '@/components/simulation/YearEndReview'
import { FiredScreen } from '@/components/simulation/FiredScreen'
import { Button } from '@/components/ui/button'
import { Loader2, ArrowLeft } from 'lucide-react'
import type { SimulationStateDTO } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

export default function SimulationPlayPage() {
  const params = useParams()
  const router = useRouter()
  const simulationId = params.id as string
  const playId = params.playId as string

  const prefersReducedMotion = usePrefersReducedMotion()

  const [state, setState] = useState<SimulationStateDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [advancing, setAdvancing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Narrative animation state
  const [contentStage, setContentStage] = useState<'entering' | 'visible' | 'exiting'>('visible')
  const [narrativeDone, setNarrativeDone] = useState(false)
  const [showDecision, setShowDecision] = useState(false)
  const [showOpeningNarrative, setShowOpeningNarrative] = useState(false)
  const [openingDone, setOpeningDone] = useState(false)
  const revealTimerRef = useRef<number | null>(null)

  // Load current state
  useEffect(() => {
    loadState()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playId])

  // After narrative finishes typing, reveal decision area
  useEffect(() => {
    if (!narrativeDone || !state || state.phase !== 'DECISION' || !state.decision) return

    if (prefersReducedMotion) {
      setShowDecision(true)
      return
    }

    revealTimerRef.current = window.setTimeout(() => setShowDecision(true), 200)
    return () => {
      if (revealTimerRef.current) window.clearTimeout(revealTimerRef.current)
    }
  }, [narrativeDone, state, prefersReducedMotion])

  // When state changes and we have an opening narrative, show it first
  useEffect(() => {
    if (!state) return

    if (state.phase === 'DECISION' && state.openingNarrative) {
      setShowOpeningNarrative(true)
      setOpeningDone(false)
      setNarrativeDone(Boolean(prefersReducedMotion))
      setShowDecision(false)
    } else if (state.phase === 'DECISION') {
      setShowOpeningNarrative(false)
      setOpeningDone(true)
      setNarrativeDone(Boolean(prefersReducedMotion))
      setShowDecision(Boolean(prefersReducedMotion))
    }

    if (!prefersReducedMotion && state.phase === 'DECISION') {
      setContentStage('entering')
      const t = window.setTimeout(() => setContentStage('visible'), 20)
      return () => window.clearTimeout(t)
    } else {
      setContentStage('visible')
    }
  }, [state?.phase, state?.currentDecision, state?.currentYear, prefersReducedMotion])

  async function loadState() {
    try {
      setLoading(true)
      setError(null)
      const data = await simulationsApi.getCurrentState(playId)
      setState(data)

      // If debrief, redirect
      if (data.phase === 'DEBRIEF') {
        router.push(`/simulations/${simulationId}/play/${playId}/debrief`)
      }
    } catch {
      setError('Failed to load the current scenario.')
    } finally {
      setLoading(false)
    }
  }

  async function handleSubmit(data: any) {
    if (!state?.decision || submitting) return

    try {
      // Fade out current content
      if (!prefersReducedMotion) {
        setContentStage('exiting')
        await sleep(200)
      }

      setSubmitting(true)
      setShowDecision(false)
      setNarrativeDone(false)
      setShowOpeningNarrative(false)
      setError(null)

      const input = {
        decisionId: state.decision.decisionId,
        choiceId: data.choiceId,
        input: data.input,
      }

      const newState = await simulationsApi.submitDecision(playId, input)
      setState(newState)

      if (newState.phase === 'DEBRIEF') {
        await sleep(prefersReducedMotion ? 500 : 1500)
        router.push(`/simulations/${simulationId}/play/${playId}/debrief`)
      }
    } catch {
      setError('Failed to submit your decision. Please try again.')
      setContentStage('visible')
      setNarrativeDone(true)
      setShowDecision(true)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleAdvanceYear() {
    if (advancing) return
    try {
      setAdvancing(true)
      setError(null)
      const newState = await simulationsApi.advanceYear(playId)
      setState(newState)

      if (newState.phase === 'DEBRIEF') {
        router.push(`/simulations/${simulationId}/play/${playId}/debrief`)
      }
    } catch {
      setError('Failed to advance to the next year.')
    } finally {
      setAdvancing(false)
    }
  }

  // FIRED phase
  if (state?.phase === 'FIRED') {
    return (
      <ProtectedRoute>
        <FiredScreen
          simulationId={simulationId}
          cumulativeScore={state.cumulativeScore}
          debrief={state.debrief}
        />
      </ProtectedRoute>
    )
  }

  // YEAR_END_REVIEW phase
  if (state?.phase === 'YEAR_END_REVIEW' && state.yearEndReview) {
    return (
      <ProtectedRoute>
        <PlayHeader
          currentRole={state.currentRole}
          currentYear={state.currentYear}
          currentDecision={state.totalDecisions}
          totalDecisions={state.totalDecisions}
          cumulativeScore={state.cumulativeScore}
        />
        <YearEndReview
          review={state.yearEndReview}
          currentYear={state.currentYear}
          onContinue={handleAdvanceYear}
          continuing={advancing}
        />
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50 flex flex-col">
        {/* Play header */}
        {state && state.phase === 'DECISION' && (
          <PlayHeader
            currentRole={state.currentRole}
            currentYear={state.currentYear}
            currentDecision={state.currentDecision}
            totalDecisions={state.totalDecisions}
            cumulativeScore={state.cumulativeScore}
          />
        )}

        {/* Minimal top bar for exit */}
        <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => router.push('/simulations')}
            className="text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4 mr-1" />
            Exit
          </Button>
          <div className="w-16" />
        </div>

        {/* Main content area */}
        <div className="flex-1 flex items-start justify-center px-4 py-8 md:py-16">
          <div className="w-full max-w-2xl">
            {error && (
              <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                {error}
              </div>
            )}

            {loading && !state ? (
              <div className="flex flex-col items-center justify-center py-20 gap-4">
                <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
                <p className="text-sm text-gray-500">Loading scenario...</p>
              </div>
            ) : state?.phase === 'DECISION' && state.decision ? (
              <div
                className={`transition-all duration-200 ${
                  contentStage === 'entering'
                    ? 'opacity-0 translate-y-2'
                    : contentStage === 'exiting'
                      ? 'opacity-0 -translate-y-1'
                      : 'opacity-100 translate-y-0'
                }`}
              >
                {/* Opening narrative (start of year) */}
                {showOpeningNarrative && state.openingNarrative && !openingDone && (
                  <div className="mb-8 p-6 bg-blue-50/50 border border-blue-100 rounded-xl">
                    <TypewriterText
                      key={`opening-${state.currentYear}`}
                      as="div"
                      className="text-base leading-relaxed text-blue-900 whitespace-pre-line"
                      text={state.openingNarrative}
                      onDone={() => setOpeningDone(true)}
                      speedMs={14}
                      startDelayMs={80}
                      cursor
                    />
                  </div>
                )}

                {/* Decision narrative */}
                {(openingDone || !state.openingNarrative) && (
                  <div className="mb-8">
                    <TypewriterText
                      key={state.decision.decisionId}
                      as="div"
                      className="text-lg leading-relaxed text-gray-800 whitespace-pre-line"
                      text={state.decision.narrative}
                      onDone={() => setNarrativeDone(true)}
                      speedMs={16}
                      startDelayMs={80}
                      cursor
                    />
                  </div>
                )}

                {/* Decision input area */}
                {showDecision && (
                  <div
                    className={`transition-opacity duration-300 ${
                      showDecision ? 'opacity-100' : 'opacity-0'
                    }`}
                  >
                    <div className="border-t border-gray-200 pt-6">
                      <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-4">
                        What do you decide?
                      </h3>
                      <DecisionInput
                        decisionType={state.decision.decisionType}
                        decisionConfig={state.decision.decisionConfig}
                        choices={state.decision.choices}
                        onSubmit={handleSubmit}
                        disabled={submitting}
                      />
                    </div>
                  </div>
                )}

                {/* Submitting overlay */}
                {submitting && (
                  <div className="flex items-center justify-center py-8 gap-3">
                    <Loader2 className="h-5 w-5 animate-spin text-primary-600" />
                    <span className="text-sm text-gray-500">Processing your decision...</span>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-center py-20">
                <p className="text-gray-500 mb-4">Could not load the scenario.</p>
                <Button onClick={loadState}>Retry</Button>
              </div>
            )}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  )
}
