'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { simulationsApi } from '@/lib/api'
import { TypewriterText, usePrefersReducedMotion } from '@/components/psych-test/TypewriterText'
import { DecisionInput } from '@/components/simulation/DecisionInput'
import { YearEndReview } from '@/components/simulation/YearEndReview'
import { FiredScreen } from '@/components/simulation/FiredScreen'
import { AdvisorDialog } from '@/components/simulation/AdvisorDialog'
import { StatusHUD } from '@/components/simulation/StatusHUD'
import { YearTransition } from '@/components/simulation/YearTransition'
import { FinancialReport } from '@/components/simulation/FinancialReport'
import { PromotionCelebration } from '@/components/simulation/PromotionCelebration'
import { Button } from '@/components/ui/button'
import { Loader2, ArrowLeft } from 'lucide-react'
import type { SimulationStateDTO } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

type PlayPhase =
  | 'LOADING'
  | 'ADVISOR_SETUP'
  | 'OPENING_NARRATIVE'
  | 'DECISION_NARRATIVE'
  | 'DECISION_ACTIVE'
  | 'SUBMITTING'
  | 'ADVISOR_REACTION'
  | 'YEAR_TRANSITION'
  | 'FINANCIAL_REPORT'
  | 'STAKEHOLDER_REVIEW'
  | 'PROMOTION'
  | 'FIRED'
  | 'DEBRIEF'

export default function SimulationPlayPage() {
  const params = useParams()
  const router = useRouter()
  const simulationId = params.id as string
  const playId = params.playId as string
  const prefersReducedMotion = usePrefersReducedMotion()

  const [state, setState] = useState<SimulationStateDTO | null>(null)
  const [playPhase, setPlayPhase] = useState<PlayPhase>('LOADING')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [narrativeDone, setNarrativeDone] = useState(false)
  const [showDecisionInput, setShowDecisionInput] = useState(false)
  const [lastReaction, setLastReaction] = useState<{ mood: string; text: string } | null>(null)
  const revealTimerRef = useRef<number | null>(null)

  // Load current state
  useEffect(() => {
    loadState()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playId])

  // After narrative finishes typing, show decision input
  useEffect(() => {
    if (!narrativeDone || playPhase !== 'DECISION_NARRATIVE') return
    if (prefersReducedMotion) {
      setPlayPhase('DECISION_ACTIVE')
      setShowDecisionInput(true)
      return
    }
    revealTimerRef.current = window.setTimeout(() => {
      setPlayPhase('DECISION_ACTIVE')
      setShowDecisionInput(true)
    }, 200)
    return () => { if (revealTimerRef.current) window.clearTimeout(revealTimerRef.current) }
  }, [narrativeDone, playPhase, prefersReducedMotion])

  async function loadState() {
    try {
      setLoading(true)
      setError(null)
      const data = await simulationsApi.getCurrentState(playId)
      setState(data)
      transitionToPhase(data)
    } catch {
      setError('Failed to load the current scenario.')
    } finally {
      setLoading(false)
    }
  }

  function transitionToPhase(data: SimulationStateDTO) {
    if (data.phase === 'DEBRIEF') {
      router.push(`/simulations/${simulationId}/play/${playId}/debrief`)
      return
    }
    if (data.phase === 'FIRED') {
      setPlayPhase('FIRED')
      return
    }
    if (data.phase === 'YEAR_END_REVIEW') {
      setPlayPhase('YEAR_TRANSITION')
      return
    }
    if (data.phase === 'DECISION') {
      // Show advisor dialog first if available
      if (data.advisorDialog) {
        setPlayPhase('ADVISOR_SETUP')
      } else if (data.openingNarrative) {
        setPlayPhase('OPENING_NARRATIVE')
        setNarrativeDone(Boolean(prefersReducedMotion))
      } else {
        setPlayPhase('DECISION_NARRATIVE')
        setNarrativeDone(Boolean(prefersReducedMotion))
        setShowDecisionInput(Boolean(prefersReducedMotion))
      }
    }
  }

  const handleAdvisorDismiss = useCallback(() => {
    if (!state) return
    if (state.openingNarrative && playPhase === 'ADVISOR_SETUP') {
      setPlayPhase('OPENING_NARRATIVE')
      setNarrativeDone(Boolean(prefersReducedMotion))
    } else if (playPhase === 'ADVISOR_SETUP') {
      setPlayPhase('DECISION_NARRATIVE')
      setNarrativeDone(Boolean(prefersReducedMotion))
      setShowDecisionInput(Boolean(prefersReducedMotion))
    }
  }, [state, playPhase, prefersReducedMotion])

  function handleOpeningDone() {
    setPlayPhase('DECISION_NARRATIVE')
    setNarrativeDone(Boolean(prefersReducedMotion))
    setShowDecisionInput(Boolean(prefersReducedMotion))
  }

  async function handleSubmit(data: any) {
    if (!state?.decision || submitting) return

    try {
      setPlayPhase('SUBMITTING')
      setSubmitting(true)
      setShowDecisionInput(false)
      setNarrativeDone(false)
      setError(null)

      const input = {
        decisionId: state.decision.decisionId,
        choiceId: data.choiceId,
        input: data.input,
      }

      const newState = await simulationsApi.submitDecision(playId, input)

      // Show advisor reaction if available from the previous decision
      const quality = data.choiceId ? 'quality_2' : 'quality_2' // Backend determines actual quality
      if (state.advisorDialog) {
        // Use a brief reaction before transitioning
        setLastReaction({ mood: 'neutral', text: 'Noted. Let\'s see how this plays out.' })
        setPlayPhase('ADVISOR_REACTION')
        await sleep(prefersReducedMotion ? 500 : 2000)
      }

      setState(newState)
      transitionToPhase(newState)
    } catch {
      setError('Failed to submit your decision. Please try again.')
      setPlayPhase('DECISION_ACTIVE')
      setNarrativeDone(true)
      setShowDecisionInput(true)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleYearTransitionComplete() {
    if (state?.yearEndReview?.fired) {
      setPlayPhase('FIRED')
      return
    }
    // Show financial report if available
    if (state?.financialReport) {
      setPlayPhase('FINANCIAL_REPORT')
    } else {
      setPlayPhase('STAKEHOLDER_REVIEW')
    }
  }

  function handleFinancialReportDismiss() {
    setPlayPhase('STAKEHOLDER_REVIEW')
  }

  async function handleAdvanceYear() {
    try {
      setError(null)

      // Show promotion if applicable
      if (state?.yearEndReview?.promotionTitle && state.currentRole) {
        setPlayPhase('PROMOTION')
        await sleep(prefersReducedMotion ? 500 : 3000)
      }

      const newState = await simulationsApi.advanceYear(playId)
      setState(newState)
      transitionToPhase(newState)
    } catch {
      setError('Failed to advance to the next year.')
    }
  }

  // FIRED phase
  if (playPhase === 'FIRED' && state) {
    return (
      <ProtectedRoute>
        <div className="min-h-screen bg-[#0F1729]">
          <FiredScreen
            simulationId={simulationId}
            cumulativeScore={state.cumulativeScore}
            debrief={state.debrief}
          />
        </div>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-[#0F1729] flex flex-col text-[#E2E8F0]">
        {/* Minimal top bar */}
        <div className="flex items-center justify-between px-4 py-2 border-b border-[#1E3A5F]/20">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => router.push('/simulations')}
            className="text-[#94A3B8] hover:text-[#E2E8F0]"
          >
            <ArrowLeft className="h-4 w-4 mr-1" />
            Exit
          </Button>
        </div>

        {/* Zone 1: Main Content Area */}
        <div className="flex-1 flex items-start justify-center px-4 py-6 md:py-10 pb-32">
          <div className="w-full max-w-2xl">
            {error && (
              <div className="mb-6 p-4 bg-red-900/20 border border-red-500/30 rounded-lg text-red-400 text-sm">
                {error}
              </div>
            )}

            {loading && !state ? (
              <div className="flex flex-col items-center justify-center py-20 gap-4">
                <Loader2 className="h-8 w-8 animate-spin text-[#0891B2]" />
                <p className="text-sm text-[#94A3B8]">Loading scenario...</p>
              </div>
            ) : playPhase === 'STAKEHOLDER_REVIEW' && state?.yearEndReview ? (
              <YearEndReview
                review={state.yearEndReview}
                currentYear={state.currentYear}
                onContinue={handleAdvanceYear}
              />
            ) : playPhase === 'FINANCIAL_REPORT' && state?.financialReport ? (
              <FinancialReport
                report={state.financialReport}
                currency="$"
                onDismiss={handleFinancialReportDismiss}
              />
            ) : state?.phase === 'DECISION' && state.decision ? (
              <div className="transition-all duration-200 opacity-100">
                {/* Opening narrative */}
                {playPhase === 'OPENING_NARRATIVE' && state.openingNarrative && (
                  <div className="mb-8 p-6 bg-[#1A2744]/50 border border-[#1E3A5F]/30 rounded-xl">
                    <TypewriterText
                      key={`opening-${state.currentYear}`}
                      as="div"
                      className="text-base leading-relaxed text-[#94A3B8] whitespace-pre-line"
                      text={state.openingNarrative}
                      onDone={handleOpeningDone}
                      speedMs={14}
                      startDelayMs={80}
                      cursor
                    />
                  </div>
                )}

                {/* Decision narrative */}
                {(playPhase === 'DECISION_NARRATIVE' || playPhase === 'DECISION_ACTIVE') && (
                  <div className="mb-8">
                    <TypewriterText
                      key={state.decision.decisionId}
                      as="div"
                      className="text-lg leading-relaxed text-[#E2E8F0] whitespace-pre-line"
                      text={state.decision.narrative}
                      onDone={() => setNarrativeDone(true)}
                      speedMs={16}
                      startDelayMs={80}
                      cursor
                    />
                  </div>
                )}

                {/* Decision input */}
                {showDecisionInput && playPhase === 'DECISION_ACTIVE' && (
                  <div className="transition-opacity duration-300 opacity-100">
                    <div className="border-t border-[#1E3A5F]/30 pt-6">
                      <h3 className="text-xs font-semibold text-[#94A3B8] uppercase tracking-wide mb-4">
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

                {/* Submitting */}
                {playPhase === 'SUBMITTING' && (
                  <div className="flex items-center justify-center py-8 gap-3">
                    <Loader2 className="h-5 w-5 animate-spin text-[#0891B2]" />
                    <span className="text-sm text-[#94A3B8]">Processing your decision...</span>
                  </div>
                )}
              </div>
            ) : !loading ? (
              <div className="text-center py-20">
                <p className="text-[#94A3B8] mb-4">Could not load the scenario.</p>
                <Button onClick={loadState}>Retry</Button>
              </div>
            ) : null}
          </div>
        </div>

        {/* Zone 2: Advisor Dialog Area */}
        {playPhase === 'ADVISOR_SETUP' && state?.advisorDialog && (
          <div className="fixed bottom-16 left-0 right-0 px-4 z-40">
            <div className="max-w-2xl mx-auto">
              <AdvisorDialog
                mood={state.advisorDialog.mood}
                text={state.advisorDialog.text}
                advisorName={state.advisorDialog.advisorName || 'Advisor'}
                onDismiss={handleAdvisorDismiss}
              />
            </div>
          </div>
        )}

        {playPhase === 'ADVISOR_REACTION' && lastReaction && (
          <div className="fixed bottom-16 left-0 right-0 px-4 z-40">
            <div className="max-w-2xl mx-auto">
              <AdvisorDialog
                mood={lastReaction.mood}
                text={lastReaction.text}
                advisorName="Advisor"
                onDismiss={() => {}}
                autoAdvance={2000}
              />
            </div>
          </div>
        )}

        {/* Zone 3: Status HUD */}
        {state && (
          <div className="fixed bottom-0 left-0 right-0 z-30">
            <StatusHUD
              role={state.currentRole || 'Unknown'}
              year={state.currentYear}
              totalYears={7} // Will be dynamic when available from simulation data
              decision={state.currentDecision}
              totalDecisions={state.totalDecisions}
              budget={state.currentBudget}
              score={state.cumulativeScore}
              performanceBand={state.performanceBand || 'STEADY'}
            />
          </div>
        )}

        {/* Overlays */}
        {playPhase === 'YEAR_TRANSITION' && state && (
          <YearTransition
            yearCompleted={state.currentYear}
            onComplete={handleYearTransitionComplete}
          />
        )}

        {playPhase === 'PROMOTION' && state?.yearEndReview?.promotionTitle && state.currentRole && (
          <PromotionCelebration
            oldTitle={state.currentRole}
            newTitle={state.yearEndReview.promotionTitle}
            onDismiss={() => {}}
          />
        )}
      </div>
    </ProtectedRoute>
  )
}
