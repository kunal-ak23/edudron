'use client'

import { useState, useEffect, useRef } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { 
  AlertCircle, 
  CheckCircle, 
  AlertTriangle, 
  XCircle,
  Camera,
  Eye,
  Download,
  Loader2,
  GitBranch,
  List
} from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { proctoringApi, type ProctoringReport as ProctoringReportType, type ProctoringEvent } from '@/lib/proctoring-api'
import { getJourneyEvents, type JourneyEvent } from '@/lib/journey-api'

interface ProctoringReportProps {
  examId: string
  submissionId: string
}

export function ProctoringReport({ examId, submissionId }: ProctoringReportProps) {
  const [report, setReport] = useState<ProctoringReportType | null>(null)
  const [loading, setLoading] = useState(true)
  const [selectedPhoto, setSelectedPhoto] = useState<string | null>(null)
  const [journeyEvents, setJourneyEvents] = useState<JourneyEvent[]>([])
  const [journeyLoading, setJourneyLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('events')
  const journeyFetchedForRef = useRef<string | null>(null)

  useEffect(() => {
    journeyFetchedForRef.current = null
    setJourneyEvents([])
  }, [submissionId])

  useEffect(() => {
    if (activeTab !== 'journey' || journeyFetchedForRef.current === submissionId) return
    journeyFetchedForRef.current = submissionId
    setJourneyLoading(true)
    getJourneyEvents(examId, submissionId)
      .then((data) => setJourneyEvents(data))
      .catch(() => {})
      .finally(() => setJourneyLoading(false))
  }, [activeTab, examId, submissionId])

  useEffect(() => {
    const loadReport = async () => {
      try {
        setLoading(true)
        const data = await proctoringApi.getReport(examId, submissionId)
        setReport(data)
      } catch (err) {
      } finally {
        setLoading(false)
      }
    }
    loadReport()
  }, [examId, submissionId])

  const getStatusBadge = (status: string) => {
    const config: Record<string, { variant: 'default' | 'secondary' | 'destructive'; icon: typeof CheckCircle; label: string; color: string }> = {
      CLEAR: { variant: 'default', icon: CheckCircle, label: 'Clear', color: 'text-green-600' },
      FLAGGED: { variant: 'secondary', icon: AlertTriangle, label: 'Flagged', color: 'text-yellow-600' },
      SUSPICIOUS: { variant: 'secondary', icon: AlertCircle, label: 'Suspicious', color: 'text-orange-600' },
      VIOLATION: { variant: 'destructive', icon: XCircle, label: 'Violation', color: 'text-red-600' }
    }
    const entry = config[status] ?? { variant: 'secondary' as const, icon: AlertCircle, label: status || 'Unknown', color: 'text-gray-600' }
    const { variant, icon: Icon, label, color } = entry

    return (
      <Badge variant={variant} className="flex items-center gap-1">
        <Icon className={`h-3 w-3 ${color}`} />
        {label}
      </Badge>
    )
  }

  const getSeverityBadge = (severity: string | null | undefined) => {
    const normalized = (severity != null && String(severity).trim()) ? String(severity).trim().toUpperCase() : null
    const variants: Record<string, { variant: 'default' | 'secondary' | 'destructive'; color: string }> = {
      INFO: { variant: 'default', color: 'text-primary-foreground' },
      WARNING: { variant: 'secondary', color: 'text-yellow-600' },
      VIOLATION: { variant: 'destructive', color: 'text-destructive-foreground' }
    }
    const displaySeverity = normalized || 'INFO'
    const entry = variants[displaySeverity] ?? { variant: 'secondary' as const, color: 'text-muted-foreground' }
    return (
      <Badge variant={entry.variant} className={entry.color}>
        {displaySeverity}
      </Badge>
    )
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-gray-400" />
      </div>
    )
  }

  if (!report) {
    return (
      <Card>
        <CardContent className="py-12">
          <div className="text-center text-gray-500">
            No proctoring data available for this submission
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      {/* Summary Card */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>Proctoring Summary</span>
            {getStatusBadge(report.proctoringStatus)}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-4 gap-4">
            <div>
              <div className="text-sm text-gray-500">Tab Switches</div>
              <div className="text-2xl font-bold">{report.tabSwitchCount ?? 0}</div>
            </div>
            <div>
              <div className="text-sm text-gray-500">Copy Attempts</div>
              <div className="text-2xl font-bold">{report.copyAttemptCount ?? 0}</div>
            </div>
            <div>
              <div className="text-sm text-gray-500">Warnings</div>
              <div className="text-2xl font-bold text-yellow-600">{report.eventCounts?.warning ?? 0}</div>
            </div>
            <div>
              <div className="text-sm text-gray-500">Violations</div>
              <div className="text-2xl font-bold text-red-600">{report.eventCounts?.violation ?? 0}</div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Tabs for detailed view */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="events">
            Events ({report.events?.length ?? 0})
          </TabsTrigger>
          <TabsTrigger value="journey">
            Assessment journey
          </TabsTrigger>
          <TabsTrigger value="photos">
            Photos ({report.proctoringData?.photos?.length || 0})
          </TabsTrigger>
        </TabsList>

        {/* Events Tab: proctoring violations (tab switches, copy attempts, etc.) */}
        <TabsContent value="events">
          <Card>
            <CardHeader>
              <CardTitle>Proctoring Events</CardTitle>
              <p className="text-sm text-gray-500 mt-1">
                Tab switches, copy attempts, and other proctoring violations. Zero entries means no such events were recorded (student followed exam rules).
              </p>
            </CardHeader>
            <CardContent>
              {(report.events ?? []).length === 0 ? (
                <div className="text-center py-8 text-gray-500">
                  No proctoring violations recorded (e.g. tab switches, copy attempts). This is normal when the student followed exam rules.
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Timestamp</TableHead>
                      <TableHead>Event Type</TableHead>
                      <TableHead>Severity</TableHead>
                      <TableHead>Details</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(report.events ?? []).map((event) => (
                      <TableRow key={event.id}>
                        <TableCell>
                          {new Date(event.createdAt).toLocaleString()}
                        </TableCell>
                        <TableCell className="font-medium">
                          {event.eventType.replace(/_/g, ' ')}
                        </TableCell>
                        <TableCell>{getSeverityBadge(event.severity)}</TableCell>
                        <TableCell className="text-sm text-gray-600">
                          {event.metadata && Object.keys(event.metadata).length > 0 ? (
                            <pre className="text-xs">{JSON.stringify(event.metadata, null, 2)}</pre>
                          ) : (
                            '-'
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Assessment journey tab: timeline + flowchart */}
        <TabsContent value="journey">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <GitBranch className="h-5 w-5" />
                Assessment journey
              </CardTitle>
              <p className="text-sm text-gray-500">
                Timeline of actions from Take test through Submit (including permission denials, auto-submit reason, drop-off).
              </p>
            </CardHeader>
            <CardContent>
              {journeyLoading ? (
                <div className="flex justify-center py-12">
                  <Loader2 className="h-8 w-8 animate-spin text-gray-400" />
                </div>
              ) : journeyEvents.length === 0 ? (
                <div className="text-center py-8 text-gray-500">
                  No journey events recorded for this submission
                </div>
              ) : (
                <div className="space-y-8">
                  {/* Vertical flowchart */}
                  <div>
                    <h4 className="text-sm font-medium text-gray-700 mb-4 flex items-center gap-2">
                      <GitBranch className="h-4 w-4" />
                      Flow
                    </h4>
                    <div className="relative pl-6 border-l-2 border-gray-200 space-y-0">
                      {journeyEvents.map((evt, idx) => {
                        const reason = evt.metadata?.reason as string | undefined
                        const label = reason
                          ? `${evt.eventType.replace(/_/g, ' ')} (${reason})`
                          : evt.eventType.replace(/_/g, ' ')
                        return (
                          <div key={evt.id} className="relative flex gap-4 pb-6 last:pb-0">
                            <div className="absolute -left-6 top-2 w-3 h-3 rounded-full bg-primary-500 border-2 border-white shadow" />
                            <div className="flex-1 min-w-0 rounded-lg border bg-gray-50/50 px-3 py-2">
                              <div className="font-medium text-sm text-gray-900">{label}</div>
                              <div className="text-xs text-gray-500 mt-0.5">
                                {new Date(evt.createdAt).toLocaleString()}
                                <span className="ml-2 inline-block">{getSeverityBadge(evt.severity)}</span>
                              </div>
                              {evt.metadata && Object.keys(evt.metadata).length > 0 && (
                                <details className="mt-2">
                                  <summary className="text-xs text-gray-500 cursor-pointer">Metadata</summary>
                                  <pre className="text-xs mt-1 p-2 bg-gray-100 rounded overflow-auto max-h-24">
                                    {JSON.stringify(evt.metadata, null, 2)}
                                  </pre>
                                </details>
                              )}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                  {/* Table timeline */}
                  <div>
                    <h4 className="text-sm font-medium text-gray-700 mb-4 flex items-center gap-2">
                      <List className="h-4 w-4" />
                      Event log
                    </h4>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Timestamp</TableHead>
                          <TableHead>Event type</TableHead>
                          <TableHead>Severity</TableHead>
                          <TableHead>Details</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {journeyEvents.map((evt) => (
                          <TableRow key={evt.id}>
                            <TableCell className="text-sm">
                              {new Date(evt.createdAt).toLocaleString()}
                            </TableCell>
                            <TableCell className="font-medium text-sm">
                              {evt.eventType.replace(/_/g, ' ')}
                            </TableCell>
                            <TableCell>{getSeverityBadge(evt.severity)}</TableCell>
                            <TableCell className="text-sm text-gray-600">
                              {evt.metadata && Object.keys(evt.metadata).length > 0 ? (
                                <pre className="text-xs whitespace-pre-wrap">{JSON.stringify(evt.metadata)}</pre>
                              ) : (
                                '-'
                              )}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Photos Tab */}
        <TabsContent value="photos">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between">
                <span>Captured Photos</span>
                {report.identityVerified && (
                  <Badge variant="default">
                    <CheckCircle className="h-3 w-3 mr-1" />
                    Identity Verified
                  </Badge>
                )}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-6">
                {/* Identity Verification Photo */}
                {report.identityVerificationPhotoUrl && (
                  <div>
                    <h4 className="text-sm font-medium mb-2">Identity Verification</h4>
                    <img
                      src={report.identityVerificationPhotoUrl}
                      alt="Identity verification"
                      className="rounded-lg border max-w-sm cursor-pointer hover:opacity-80 transition"
                      onClick={() => setSelectedPhoto(report.identityVerificationPhotoUrl!)}
                    />
                  </div>
                )}

                {/* Captured Photos Grid */}
                {report.proctoringData?.photos && report.proctoringData.photos.length > 0 && (
                  <div>
                    <h4 className="text-sm font-medium mb-2">Captured During Exam</h4>
                    <div className="grid grid-cols-4 gap-4">
                      {report.proctoringData.photos.map((photo, index) => (
                        <div key={index} className="space-y-1">
                          <img
                            src={photo.url}
                            alt={`Capture ${index + 1}`}
                            className="rounded-lg border aspect-video object-cover cursor-pointer hover:opacity-80 transition"
                            onClick={() => setSelectedPhoto(photo.url)}
                          />
                          <div className="text-xs text-gray-500 text-center">
                            {new Date(photo.capturedAt).toLocaleTimeString()}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {!report.identityVerificationPhotoUrl && 
                 (!report.proctoringData?.photos || report.proctoringData.photos.length === 0) && (
                  <div className="text-center py-8 text-gray-500">
                    <Camera className="h-12 w-12 mx-auto mb-2 opacity-50" />
                    No photos captured
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Photo Lightbox */}
      {selectedPhoto && (
        <div
          className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4"
          onClick={() => setSelectedPhoto(null)}
        >
          <div className="relative max-w-4xl max-h-full">
            <img
              src={selectedPhoto}
              alt="Full size"
              className="max-w-full max-h-screen rounded-lg"
            />
            <Button
              variant="secondary"
              size="sm"
              className="absolute top-4 right-4"
              onClick={() => setSelectedPhoto(null)}
            >
              Close
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
