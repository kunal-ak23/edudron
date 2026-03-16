'use client'

import { useEffect, useRef, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { simulationsApi } from '@/lib/api'
import { TypewriterText, usePrefersReducedMotion } from '@/components/psych-test/TypewriterText'
import { DecisionInput } from '@/components/simulation/DecisionInput'
import { Button } from '@/components/ui/button'
import { Loader2, ArrowLeft } from 'lucide-react'
import type { SimulationNodeDTO, DecisionInput as DecisionInputType } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

export default function SimulationPlayPage() {
  const params = useParams()
  const router = useRouter()
  const simulationId = params.id as string
  const playId = params.playId as string

  const prefersReducedMotion = usePrefersReducedMotion()

  const [node, setNode] = useState<SimulationNodeDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [decisionCount, setDecisionCount] = useState(0)

  // Narrative animation state
  const [contentStage, setContentStage] = useState<'entering' | 'visible' | 'exiting'>('visible')
  const [narrativeDone, setNarrativeDone] = useState(false)
  const [showDecision, setShowDecision] = useState(false)
  const revealTimerRef = useRef<number | null>(null)

  // Load current node
  useEffect(() => {
    loadCurrentNode()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [simulationId, playId])

  // After narrative finishes typing, reveal decision area
  useEffect(() => {
    if (!narrativeDone || !node || node.terminal) return

    if (prefersReducedMotion) {
      setShowDecision(true)
      return
    }

    revealTimerRef.current = window.setTimeout(() => setShowDecision(true), 200)
    return () => {
      if (revealTimerRef.current) window.clearTimeout(revealTimerRef.current)
    }
  }, [narrativeDone, node, prefersReducedMotion])

  // Reset animation state when node changes
  useEffect(() => {
    if (!node) return
    setNarrativeDone(Boolean(prefersReducedMotion))
    setShowDecision(Boolean(prefersReducedMotion && !node.terminal))
    if (!prefersReducedMotion) {
      setContentStage('entering')
      const t = window.setTimeout(() => setContentStage('visible'), 20)
      return () => window.clearTimeout(t)
    } else {
      setContentStage('visible')
    }
  }, [node?.nodeId, prefersReducedMotion])

  async function loadCurrentNode() {
    try {
      setLoading(true)
      setError(null)
      const data = await simulationsApi.getCurrentNode(simulationId, playId)
      setNode(data)

      // If terminal, redirect to debrief after a brief pause
      if (data.terminal) {
        await sleep(prefersReducedMotion ? 500 : 2000)
        router.push(`/simulations/${simulationId}/play/${playId}/debrief`)
      }
    } catch (e: any) {
      setError('Failed to load the current scenario.')
    } finally {
      setLoading(false)
    }
  }

  async function handleSubmit(data: any) {
    if (!node || submitting) return

    try {
      // Fade out current content
      if (!prefersReducedMotion) {
        setContentStage('exiting')
        await sleep(200)
      }

      setSubmitting(true)
      setShowDecision(false)
      setNarrativeDone(false)
      setError(null)

      const input: DecisionInputType = {
        nodeId: node.nodeId,
        choiceId: data.choiceId,
        input: data.input,
      }

      const nextNode = await simulationsApi.submitDecision(simulationId, playId, input)
      setDecisionCount((c) => c + 1)
      setNode(nextNode)

      if (nextNode.terminal) {
        await sleep(prefersReducedMotion ? 500 : 2000)
        router.push(`/simulations/${simulationId}/play/${playId}/debrief`)
      }
    } catch (e: any) {
      setError('Failed to submit your decision. Please try again.')
      // Restore the current node
      setContentStage('visible')
      setNarrativeDone(true)
      setShowDecision(true)
    } finally {
      setSubmitting(false)
    }
  }

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
            Exit
          </Button>
          <div className="text-sm text-gray-400 font-medium">
            Chapter {decisionCount + 1}
          </div>
          <div className="w-16" /> {/* Spacer for centering */}
        </div>

        {/* Main content area */}
        <div className="flex-1 flex items-start justify-center px-4 py-8 md:py-16">
          <div className="w-full max-w-2xl">
            {error && (
              <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                {error}
              </div>
            )}

            {loading && !node ? (
              <div className="flex flex-col items-center justify-center py-20 gap-4">
                <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
                <p className="text-sm text-gray-500">Loading scenario...</p>
              </div>
            ) : node ? (
              <div
                className={`transition-all duration-200 ${
                  contentStage === 'entering'
                    ? 'opacity-0 translate-y-2'
                    : contentStage === 'exiting'
                      ? 'opacity-0 -translate-y-1'
                      : 'opacity-100 translate-y-0'
                }`}
              >
                {/* Narrative */}
                <div className="mb-8">
                  <TypewriterText
                    key={node.nodeId}
                    as="div"
                    className="text-lg leading-relaxed text-gray-800 whitespace-pre-line"
                    text={node.narrative}
                    onDone={() => setNarrativeDone(true)}
                    speedMs={16}
                    startDelayMs={80}
                    cursor
                  />
                </div>

                {/* Terminal node message */}
                {node.terminal && narrativeDone && (
                  <div className="text-center py-8">
                    <Loader2 className="h-6 w-6 animate-spin text-primary-600 mx-auto mb-3" />
                    <p className="text-sm text-gray-500">Preparing your debrief...</p>
                  </div>
                )}

                {/* Decision input area */}
                {!node.terminal && showDecision && (
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
                        decisionType={node.decisionType}
                        decisionConfig={node.decisionConfig}
                        choices={node.choices}
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
                <Button onClick={loadCurrentNode}>Retry</Button>
              </div>
            )}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  )
}
