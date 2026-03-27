'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { simulationsApi } from '@/lib/api'
import { useSimulationFeature } from '@/hooks/useSimulationFeature'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Loader2, Gamepad2, Play, Trophy, RotateCcw } from 'lucide-react'
import type { SimulationDTO, SimulationPlayDTO } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

export default function SimulationsPage() {
  const router = useRouter()
  const { enabled, loading: featureLoading } = useSimulationFeature()
  const [simulations, setSimulations] = useState<SimulationDTO[]>([])
  const [activePlays, setActivePlays] = useState<Record<string, SimulationPlayDTO>>({})
  const [loading, setLoading] = useState(true)
  const [startingId, setStartingId] = useState<string | null>(null)

  useEffect(() => {
    if (!enabled) return
    loadData()
  }, [enabled])

  async function loadData() {
    try {
      const [sims, history] = await Promise.all([
        simulationsApi.getAvailableSimulations(),
        simulationsApi.getAllPlayHistory().catch(() => [] as SimulationPlayDTO[]),
      ])
      setSimulations(sims)

      // Build map of simulationId → most recent IN_PROGRESS play
      const active: Record<string, SimulationPlayDTO> = {}
      for (const play of history) {
        if (play.status === 'IN_PROGRESS' && play.simulationId) {
          // Keep the most recent one (history is ordered by startedAt desc)
          if (!active[play.simulationId]) {
            active[play.simulationId] = play
          }
        }
      }
      setActivePlays(active)
    } catch (err) {
      console.error('Failed to load simulations', err)
    } finally {
      setLoading(false)
    }
  }

  async function handlePlay(simulationId: string) {
    try {
      setStartingId(simulationId)
      const play = await simulationsApi.startPlay(simulationId)
      router.push(`/simulations/${simulationId}/play/${play.id}`)
    } catch (err) {
      console.error('Failed to start play', err)
      setStartingId(null)
    }
  }

  function handleResume(simulationId: string, playId: string) {
    router.push(`/simulations/${simulationId}/play/${playId}`)
  }

  if (featureLoading) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="flex justify-center items-center p-12">
            <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  if (!enabled) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="p-12 text-center text-gray-500">
            Simulations are not available for your institution.
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8">
          <div className="flex items-center gap-3 mb-6">
            <Gamepad2 className="h-8 w-8 text-primary-600" />
            <div>
              <h1 className="text-2xl font-bold text-gray-900">Simulations</h1>
              <p className="text-gray-500">Learn through immersive decision-making scenarios</p>
            </div>
          </div>

          {loading ? (
            <div className="flex justify-center p-12">
              <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
            </div>
          ) : simulations.length === 0 ? (
            <div className="text-center p-12 text-gray-500">
              No simulations available yet. Check back soon!
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {simulations.map((sim) => {
                const activePlay = activePlays[sim.id]
                return (
                  <Card key={sim.id} className="hover:shadow-md transition-shadow">
                    <CardHeader>
                      <div className="flex items-start justify-between gap-2">
                        <CardTitle className="text-lg">{sim.title}</CardTitle>
                        <Badge variant="outline" className="shrink-0">{sim.audience}</Badge>
                      </div>
                      <Badge className="w-fit">{sim.subject}</Badge>
                    </CardHeader>
                    <CardContent>
                      <p className="text-sm text-gray-500 mb-4 line-clamp-3">
                        {sim.description || 'An immersive decision-making simulation.'}
                      </p>

                      {/* Active play progress indicator */}
                      {activePlay && (
                        <div className="mb-3 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg">
                          <p className="text-xs font-medium text-amber-700">
                            In Progress — Year {activePlay.currentYear}, Decision {activePlay.currentDecision}
                            {activePlay.cumulativeScore != null && ` · ${activePlay.cumulativeScore} pts`}
                          </p>
                        </div>
                      )}

                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 text-sm text-gray-500">
                          <Trophy className="h-4 w-4" />
                          <span>{sim.totalPlays} {sim.totalPlays === 1 ? 'play' : 'plays'}</span>
                        </div>
                        <div className="flex gap-2">
                          {activePlay && (
                            <Button
                              size="sm"
                              variant="default"
                              onClick={() => handleResume(sim.id, activePlay.id)}
                            >
                              <RotateCcw className="h-4 w-4 mr-1" />
                              Resume
                            </Button>
                          )}
                          <Button
                            size="sm"
                            variant={activePlay ? 'outline' : 'default'}
                            onClick={() => handlePlay(sim.id)}
                            disabled={startingId === sim.id}
                          >
                            {startingId === sim.id ? (
                              <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                            ) : (
                              <Play className="h-4 w-4 mr-1" />
                            )}
                            {activePlay ? 'New Game' : 'Play'}
                          </Button>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          )}
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}
