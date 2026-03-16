'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { simulationsApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { Loader2, ArrowLeft, Play, History } from 'lucide-react'
import type { SimulationPlayDTO } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

function scoreColor(score?: number): string {
  if (score == null) return 'text-gray-400'
  if (score <= 30) return 'text-red-500'
  if (score <= 60) return 'text-yellow-500'
  if (score <= 80) return 'text-blue-500'
  return 'text-green-500'
}

const STATUS_STYLES: Record<string, string> = {
  COMPLETED: 'bg-green-100 text-green-700 border-green-300',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700 border-yellow-300',
  ABANDONED: 'bg-gray-100 text-gray-500 border-gray-300',
  FIRED: 'bg-red-100 text-red-700 border-red-300',
}

export default function SimulationHistoryPage() {
  const params = useParams()
  const router = useRouter()
  const simulationId = params.id as string

  const [plays, setPlays] = useState<SimulationPlayDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [startingPlay, setStartingPlay] = useState(false)

  useEffect(() => {
    loadHistory()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [simulationId])

  async function loadHistory() {
    try {
      setLoading(true)
      setError(null)
      const data = await simulationsApi.getPlayHistory(simulationId)
      setPlays(data)
    } catch {
      setError('Failed to load play history.')
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

  function formatDate(dateStr?: string): string {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    })
  }

  function formatStatus(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In Progress'
      case 'COMPLETED': return 'Completed'
      case 'FIRED': return 'Fired'
      case 'ABANDONED': return 'Abandoned'
      default: return status
    }
  }

  // Derive title from first play entry
  const simulationTitle =
    plays.length > 0 ? plays[0].simulationTitle : 'Simulation'

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8">
          {/* Header */}
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              <History className="h-7 w-7 text-primary-600" />
              <div>
                <h1 className="text-2xl font-bold text-gray-900">
                  {simulationTitle}
                </h1>
                <p className="text-gray-500">Play History</p>
              </div>
            </div>
            <Button
              onClick={handlePlayAgain}
              disabled={startingPlay}
            >
              {startingPlay ? (
                <Loader2 className="h-4 w-4 mr-1 animate-spin" />
              ) : (
                <Play className="h-4 w-4 mr-1" />
              )}
              Play Again
            </Button>
          </div>

          {error && (
            <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
              {error}
            </div>
          )}

          {loading ? (
            <div className="flex justify-center p-12">
              <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
            </div>
          ) : plays.length === 0 ? (
            <Card className="p-8 text-center text-gray-500">
              No play history yet. Start your first play!
            </Card>
          ) : (
            <Card className="overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">
                        Attempt
                      </th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">
                        Date
                      </th>
                      <th className="text-center px-4 py-3 font-medium text-gray-600">
                        Years Completed
                      </th>
                      <th className="text-center px-4 py-3 font-medium text-gray-600">
                        Final Role
                      </th>
                      <th className="text-center px-4 py-3 font-medium text-gray-600">
                        Score
                      </th>
                      <th className="text-center px-4 py-3 font-medium text-gray-600">
                        Status
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {plays.map((play) => (
                      <tr
                        key={play.id}
                        className="border-b border-gray-100 last:border-0 hover:bg-gray-50"
                      >
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-gray-900">
                              #{play.attemptNumber}
                            </span>
                            {play.isPrimary && (
                              <Badge
                                variant="outline"
                                className="bg-primary-50 text-primary-700 border-primary-200 text-xs"
                              >
                                Primary
                              </Badge>
                            )}
                          </div>
                        </td>
                        <td className="px-4 py-3 text-gray-600">
                          {formatDate(play.startedAt)}
                        </td>
                        <td className="px-4 py-3 text-center text-gray-600">
                          {play.currentYear ?? '-'}
                        </td>
                        <td className="px-4 py-3 text-center text-gray-600">
                          {play.currentRole || '-'}
                        </td>
                        <td className="px-4 py-3 text-center">
                          <span
                            className={`font-semibold tabular-nums ${scoreColor(play.finalScore ?? play.cumulativeScore)}`}
                          >
                            {play.finalScore != null
                              ? play.finalScore
                              : play.cumulativeScore != null
                                ? play.cumulativeScore
                                : '-'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-center">
                          <Badge
                            variant="outline"
                            className={
                              STATUS_STYLES[play.status] || ''
                            }
                          >
                            {formatStatus(play.status)}
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}

          {/* Navigation */}
          <div className="mt-6">
            <button
              onClick={() => router.push('/simulations')}
              className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              Back to Simulations
            </button>
          </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}
