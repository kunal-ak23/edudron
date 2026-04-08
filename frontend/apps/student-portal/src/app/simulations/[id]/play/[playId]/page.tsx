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
import ConceptPanel from '@/components/simulation/ConceptPanel'
import DashboardPanel from '@/components/simulation/DashboardPanel'
import { PostDecisionFeedback } from '@/components/simulation/PostDecisionFeedback'
import { HighlightedText, filterMatchingKeywords } from '@/components/simulation/HighlightedText'
import { Button } from '@/components/ui/button'
import { Loader2, ArrowLeft } from 'lucide-react'
import type { SimulationStateDTO, SimulationDTO } from '@kunal-ak23/edudron-shared-utils'

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

type MobileTab = 'game' | 'concepts' | 'dashboard'

export default function SimulationPlayPage() {
  const params = useParams()
  const router = useRouter()
  const simulationId = params.id as string
  const playId = params.playId as string
  const prefersReducedMotion = usePrefersReducedMotion()

  const [state, setState] = useState<SimulationStateDTO | null>(null)
  const [simulation, setSimulation] = useState<SimulationDTO | null>(null)
  const [playPhase, setPlayPhase] = useState<PlayPhase>('LOADING')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showDecisionInput, setShowDecisionInput] = useState(false)
  const [lastReaction, setLastReaction] = useState<{ mood: string; text: string } | null>(null)
  const [mobileScoreToast, setMobileScoreToast] = useState<{ delta: number; visible: boolean } | null>(null)
  const [openingNarrativeDone, setOpeningNarrativeDone] = useState(false)
  const [decisionNarrativeDone, setDecisionNarrativeDone] = useState(false)
  const [mobileTab, setMobileTab] = useState<MobileTab>('game')
  const [budgetHistory, setBudgetHistory] = useState<number[]>([])
  const [consecutiveStruggling, setConsecutiveStruggling] = useState(0)
  const revealTimerRef = useRef<number | null>(null)
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastReviewedYearRef = useRef<number>(0)

  // Load simulation metadata (for concept)
  useEffect(() => {
    simulationsApi.getSimulationDetails(simulationId).then(setSimulation).catch(() => {})
  }, [simulationId])

  // Load current state
  useEffect(() => {
    loadState()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playId])

  // Clean up toast timer on unmount
  useEffect(() => {
    return () => {
      if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    }
  }, [])

  // After decision narrative finishes typing, wait for user to click Continue
  function handleDecisionNarrativeTypingDone() {
    setDecisionNarrativeDone(true)
  }

  function handleDecisionNarrativeContinue() {
    setDecisionNarrativeDone(false)
    setPlayPhase('DECISION_ACTIVE')
    setShowDecisionInput(true)
  }

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
      // Record budget at year end for sparkline — key on currentYear so repeated
      // transitions to this phase (e.g. re-fetches) don't double-append.
      if (data.currentBudget !== undefined && data.currentBudget !== null) {
        const budget = data.currentBudget
        const year = data.currentYear
        setBudgetHistory((prev) => (prev.length >= year ? prev : [...prev, budget]))
      }
      // Track consecutive struggling years — also keyed on currentYear to avoid
      // incrementing multiple times for the same year.
      if (lastReviewedYearRef.current !== data.currentYear) {
        lastReviewedYearRef.current = data.currentYear
        if (data.performanceBand === 'STRUGGLING') {
          setConsecutiveStruggling((prev) => prev + 1)
        } else {
          setConsecutiveStruggling(0)
        }
      }
      setPlayPhase('YEAR_TRANSITION')
      return
    }
    if (data.phase === 'DECISION') {
      // Show advisor dialog first if available
      if (data.advisorDialog) {
        setPlayPhase('ADVISOR_SETUP')
      } else if (data.openingNarrative) {
        if (prefersReducedMotion) {
          // Skip opening narrative, go straight to decision
          setPlayPhase('DECISION_ACTIVE')
          setShowDecisionInput(true)
        } else {
          setPlayPhase('OPENING_NARRATIVE')
        }
      } else {
        if (prefersReducedMotion) {
          // Skip decision narrative typing, go straight to input
          setPlayPhase('DECISION_ACTIVE')
          setShowDecisionInput(true)
        } else {
          setPlayPhase('DECISION_NARRATIVE')
        }
      }
    }
  }

  const handleAdvisorDismiss = useCallback(() => {
    if (!state) return
    if (state.openingNarrative && playPhase === 'ADVISOR_SETUP') {
      if (prefersReducedMotion) {
        setPlayPhase('DECISION_ACTIVE')
        setShowDecisionInput(true)
      } else {
        setPlayPhase('OPENING_NARRATIVE')
      }
    } else if (playPhase === 'ADVISOR_SETUP') {
      if (prefersReducedMotion) {
        setPlayPhase('DECISION_ACTIVE')
        setShowDecisionInput(true)
      } else {
        setPlayPhase('DECISION_NARRATIVE')
      }
    }
  }, [state, playPhase, prefersReducedMotion])

  function handleOpeningTypingDone() {
    setOpeningNarrativeDone(true)
  }

  function handleOpeningContinue() {
    setOpeningNarrativeDone(false)
    setPlayPhase('DECISION_NARRATIVE')
  }

  async function handleSubmit(data: any) {
    if (!state?.decision || submitting) return

    try {
      setPlayPhase('SUBMITTING')
      setSubmitting(true)
      setShowDecisionInput(false)
      setError(null)

      const input = {
        decisionId: state.decision.decisionId,
        choiceId: data.choiceId,
        input: data.input,
      }

      const newState = await simulationsApi.submitDecision(playId, input)

      // Show mobile score toast — clear any prior timer to avoid leaks/races.
      if (newState.scoreDelta != null) {
        if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
        setMobileScoreToast({ delta: newState.scoreDelta, visible: true })
        toastTimerRef.current = setTimeout(() => {
          setMobileScoreToast(null)
          toastTimerRef.current = null
        }, 2000)
      }

      // Show advisor reaction if returned from backend
      if (newState.advisorReaction) {
        setLastReaction({
          mood: newState.advisorReaction.mood,
          text: newState.advisorReaction.text,
        })
        setState(newState)
        setPlayPhase('ADVISOR_REACTION')
        // Wait for user to dismiss via click — don't auto-transition
      } else {
        setState(newState)
        transitionToPhase(newState)
      }
    } catch {
      setError('Failed to submit your decision. Please try again.')
      setPlayPhase('DECISION_ACTIVE')
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

  // Gameplay content (center panel)
  const gameplayContent = (
    <>
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
                onDone={handleOpeningTypingDone}
                speedMs={14}
                startDelayMs={80}
                cursor
              />
              {openingNarrativeDone && (
                <div className="mt-4 flex justify-center">
                  <Button
                    variant="outline"
                    onClick={handleOpeningContinue}
                    className="border-[#1E3A5F] text-[#E2E8F0] hover:bg-[#1A2744]"
                  >
                    Continue
                  </Button>
                </div>
              )}
            </div>
          )}

          {/* Decision narrative */}
          {(playPhase === 'DECISION_NARRATIVE' || playPhase === 'DECISION_ACTIVE') && (
            <div className="mb-8">
              {decisionNarrativeDone ? (
                <div className="text-lg leading-relaxed text-[#E2E8F0] whitespace-pre-line">
                  <HighlightedText
                    text={state.decision.narrative}
                    keywords={((state.decision as any)?.conceptKeywords || []).map((k: any) => k.term)}
                  />
                </div>
              ) : (
                <TypewriterText
                  key={state.decision.decisionId}
                  as="div"
                  className="text-lg leading-relaxed text-[#E2E8F0] whitespace-pre-line"
                  text={state.decision.narrative}
                  onDone={handleDecisionNarrativeTypingDone}
                  speedMs={16}
                  startDelayMs={80}
                  cursor
                />
              )}
              {decisionNarrativeDone && playPhase === 'DECISION_NARRATIVE' && (
                <div className="mt-6 flex justify-center">
                  <Button
                    variant="outline"
                    onClick={handleDecisionNarrativeContinue}
                    className="border-[#1E3A5F] text-[#E2E8F0] hover:bg-[#1A2744]"
                  >
                    Make your decision
                  </Button>
                </div>
              )}
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
                  mentorGuidance={(state.decision as any).mentorGuidance}
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
    </>
  )

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
          {/* Temp: Restart for testing */}
          <Button
            variant="ghost"
            size="sm"
            onClick={async () => {
              try {
                await simulationsApi.abandonPlay(playId)
                const newPlay = await simulationsApi.startPlay(simulationId)
                router.push(`/simulations/${simulationId}/play/${newPlay.id}`)
              } catch (e) {
                console.error('Restart failed', e)
              }
            }}
            className="text-amber-400 hover:text-amber-300 text-xs"
          >
            Restart (dev)
          </Button>
        </div>

        {/* Main 3-panel area */}
        <div className="flex flex-1 overflow-hidden">
          {/* Left Panel - Concept (hidden on mobile/tablet) */}
          <aside className="hidden xl:flex flex-col w-[280px] bg-[#131b2d] border-r border-white/5 overflow-y-auto shrink-0">
            <ConceptPanel
              concept={simulation?.concept || ''}
              keywords={filterMatchingKeywords(
                state?.decision?.narrative || '',
                (state?.decision as any)?.conceptKeywords || []
              )}
              keyInsights={state?.keyInsights || []}
            />
          </aside>

          {/* Center Panel - gameplay (or mobile panel content) */}
          <div className="flex-1 min-w-0 overflow-y-auto pb-32">
            {/* Mobile: show concepts panel */}
            {mobileTab === 'concepts' && (
              <div className="xl:hidden">
                <ConceptPanel
                  concept={simulation?.concept || ''}
                  keywords={filterMatchingKeywords(
                    state?.decision?.narrative || '',
                    (state?.decision as any)?.conceptKeywords || []
                  )}
                  keyInsights={state?.keyInsights || []}
                />
              </div>
            )}

            {/* Mobile: show dashboard panel */}
            {mobileTab === 'dashboard' && (
              <div className="xl:hidden">
                {state && (
                  <DashboardPanel
                    score={state.cumulativeScore || 0}
                    budget={state.currentBudget || 0}
                    role={state.currentRole || ''}
                    performanceBand={state.performanceBand || 'STEADY'}
                    currentYear={state.currentYear || 1}
                    totalYears={state.totalYears || 4}
                    currentDecision={state.currentDecision || 1}
                    totalDecisions={state.totalDecisions || 5}
                    decisionHistory={state.decisionHistory || []}
                    goodDecisionCount={state.goodDecisionCount || 0}
                    badDecisionCount={state.badDecisionCount || 0}
                    neutralDecisionCount={state.neutralDecisionCount || 0}
                    keyInsights={state.keyInsights || []}
                    consecutiveStruggling={consecutiveStruggling}
                    budgetHistory={budgetHistory}
                  />
                )}
              </div>
            )}

            {/* Game content: always visible on desktop, conditional on mobile */}
            <div className={`px-4 py-6 md:px-12 md:py-10 ${mobileTab !== 'game' ? 'hidden xl:block' : ''}`}>
              <div className="max-w-3xl mx-auto">
                {gameplayContent}
              </div>
            </div>
          </div>

          {/* Right Panel - Dashboard (hidden on mobile/tablet) */}
          <aside className="hidden xl:flex flex-col w-[320px] bg-[#131b2d] border-l border-white/5 overflow-y-auto shrink-0">
            {state && (
              <DashboardPanel
                score={state.cumulativeScore || 0}
                budget={state.currentBudget || 0}
                role={state.currentRole || ''}
                performanceBand={state.performanceBand || 'STEADY'}
                currentYear={state.currentYear || 1}
                totalYears={state.totalYears || 4}
                currentDecision={state.currentDecision || 1}
                totalDecisions={state.totalDecisions || 5}
                decisionHistory={state.decisionHistory || []}
                goodDecisionCount={state.goodDecisionCount || 0}
                badDecisionCount={state.badDecisionCount || 0}
                neutralDecisionCount={state.neutralDecisionCount || 0}
                keyInsights={state.keyInsights || []}
              />
            )}
          </aside>
        </div>

        {/* Mobile panel tabs - shown only on mobile/tablet */}
        <div className="xl:hidden fixed bottom-0 left-0 w-full flex justify-around items-center h-16 bg-[#131b2d]/90 backdrop-blur-2xl border-t border-white/10 z-[100]">
          <button className={`flex flex-col items-center ${mobileTab === 'game' ? 'text-[#6cd3f7]' : 'text-slate-500'}`} onClick={() => setMobileTab('game')}>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" /></svg>
            <span className="text-[10px] uppercase font-bold mt-0.5">Game</span>
          </button>
          <button className={`flex flex-col items-center ${mobileTab === 'concepts' ? 'text-[#6cd3f7]' : 'text-slate-500'}`} onClick={() => setMobileTab('concepts')}>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25" /></svg>
            <span className="text-[10px] uppercase font-bold mt-0.5">Concepts</span>
          </button>
          <button className={`flex flex-col items-center ${mobileTab === 'dashboard' ? 'text-[#6cd3f7]' : 'text-slate-500'}`} onClick={() => setMobileTab('dashboard')}>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>
            <span className="text-[10px] uppercase font-bold mt-0.5">Dashboard</span>
          </button>
        </div>

        {/* Zone 2: Advisor Dialog Area */}
        {playPhase === 'ADVISOR_SETUP' && state?.advisorDialog && (
          <div className="fixed bottom-16 left-0 right-0 px-4 z-40">
            <div className="max-w-2xl mx-auto">
              <AdvisorDialog
                mood={state.advisorDialog.mood}
                text={state.advisorDialog.text}
                advisorName={state.advisorDialog.advisorName || 'Advisor'}
                characterId={state.advisorDialog.characterId}
                onDismiss={handleAdvisorDismiss}
              />
            </div>
          </div>
        )}

        {playPhase === 'ADVISOR_REACTION' && lastReaction && state && (
          <div className="fixed bottom-16 left-0 right-0 px-4 z-40">
            <div className="max-w-2xl mx-auto space-y-2">
              {/* Post-decision feedback card (desktop) */}
              <div className="hidden xl:block">
                <PostDecisionFeedback
                  scoreDelta={state.scoreDelta}
                  impactDescription={state.impactDescription}
                  metricImpacts={state.metricImpacts}
                />
              </div>
              <AdvisorDialog
                mood={lastReaction.mood}
                text={lastReaction.text}
                advisorName={state.advisorDialog?.advisorName || 'Advisor'}
                characterId={state.advisorDialog?.characterId}
                onDismiss={() => transitionToPhase(state)}
              />
            </div>
          </div>
        )}

        {/* Mobile score toast — shown briefly after decision submission */}
        {mobileScoreToast && (
          <div className="xl:hidden fixed top-0 left-0 right-0 z-50 flex justify-center pt-2 px-4">
            <div className={`w-full max-w-sm text-center py-2.5 px-4 rounded-full text-sm font-bold shadow-lg transition-opacity duration-300 ${
              mobileScoreToast.delta >= 10 ? 'bg-green-700 text-white' :
              mobileScoreToast.delta >= 5 ? 'bg-amber-600 text-white' :
              'bg-slate-700 text-slate-300'
            }`}>
              {mobileScoreToast.delta > 0 ? `+${mobileScoreToast.delta}` : mobileScoreToast.delta} pts
            </div>
          </div>
        )}

        {/* Zone 3: Status HUD */}
        {state && (
          <div className="fixed bottom-0 left-0 right-0 z-30 hidden xl:block">
            <StatusHUD
              role={state.currentRole || 'Unknown'}
              year={state.currentYear}
              totalYears={(state as any).totalYears || 7}
              decision={state.currentDecision}
              totalDecisions={state.totalDecisions}
              budget={state.currentBudget}
              score={state.cumulativeScore}
              performanceBand={state.performanceBand || 'STEADY'}
            />
          </div>
        )}

        {/* Overlays */}
        {playPhase === 'YEAR_TRANSITION' && state && (() => {
          const retYear = (state.advisorDialog as any)?.retirementYear
          const farewell = (state.advisorDialog as any)?.farewellMessage
          const advisorName = state.advisorDialog?.advisorName || 'Mentor'
          const isFarewellYear = retYear && state.currentYear === retYear - 1
          return (
            <YearTransition
              yearCompleted={state.currentYear}
              onComplete={handleYearTransitionComplete}
              mentorFarewell={isFarewellYear && farewell ? {
                advisorName,
                farewellMessage: farewell,
                characterId: state.advisorDialog?.characterId,
              } : undefined}
            />
          )
        })()}

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
