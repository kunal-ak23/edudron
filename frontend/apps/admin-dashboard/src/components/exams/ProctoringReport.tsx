'use client'

import { useState, useEffect } from 'react'
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
  Loader2
} from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { proctoringApi, type ProctoringReport as ProctoringReportType, type ProctoringEvent } from '@/lib/proctoring-api'

interface ProctoringReportProps {
  examId: string
  submissionId: string
}

export function ProctoringReport({ examId, submissionId }: ProctoringReportProps) {
  const [report, setReport] = useState<ProctoringReportType | null>(null)
  const [loading, setLoading] = useState(true)
  const [selectedPhoto, setSelectedPhoto] = useState<string | null>(null)

  useEffect(() => {
    loadReport()
  }, [examId, submissionId])

  const loadReport = async () => {
    try {
      setLoading(true)
      const data = await proctoringApi.getReport(examId, submissionId)
      setReport(data)
    } catch (err) {
      console.error('Failed to load proctoring report:', err)
    } finally {
      setLoading(false)
    }
  }

  const getStatusBadge = (status: string) => {
    const config = {
      CLEAR: { variant: 'default' as const, icon: CheckCircle, label: 'Clear', color: 'text-green-600' },
      FLAGGED: { variant: 'secondary' as const, icon: AlertTriangle, label: 'Flagged', color: 'text-yellow-600' },
      SUSPICIOUS: { variant: 'secondary' as const, icon: AlertCircle, label: 'Suspicious', color: 'text-orange-600' },
      VIOLATION: { variant: 'destructive' as const, icon: XCircle, label: 'Violation', color: 'text-red-600' }
    }

    const { variant, icon: Icon, label, color } = config[status as keyof typeof config]

    return (
      <Badge variant={variant} className="flex items-center gap-1">
        <Icon className={`h-3 w-3 ${color}`} />
        {label}
      </Badge>
    )
  }

  const getSeverityBadge = (severity: string) => {
    const variants = {
      INFO: { variant: 'default' as const, color: 'text-blue-600' },
      WARNING: { variant: 'secondary' as const, color: 'text-yellow-600' },
      VIOLATION: { variant: 'destructive' as const, color: 'text-red-600' }
    }
    const { variant, color } = variants[severity as keyof typeof variants]
    return <Badge variant={variant} className={color}>{severity}</Badge>
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
              <div className="text-2xl font-bold">{report.tabSwitchCount}</div>
            </div>
            <div>
              <div className="text-sm text-gray-500">Copy Attempts</div>
              <div className="text-2xl font-bold">{report.copyAttemptCount}</div>
            </div>
            <div>
              <div className="text-sm text-gray-500">Warnings</div>
              <div className="text-2xl font-bold text-yellow-600">{report.eventCounts.warning}</div>
            </div>
            <div>
              <div className="text-sm text-gray-500">Violations</div>
              <div className="text-2xl font-bold text-red-600">{report.eventCounts.violation}</div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Tabs for detailed view */}
      <Tabs defaultValue="events">
        <TabsList>
          <TabsTrigger value="events">
            Events ({report.events.length})
          </TabsTrigger>
          <TabsTrigger value="photos">
            Photos ({report.proctoringData?.photos?.length || 0})
          </TabsTrigger>
        </TabsList>

        {/* Events Tab */}
        <TabsContent value="events">
          <Card>
            <CardHeader>
              <CardTitle>Proctoring Events</CardTitle>
            </CardHeader>
            <CardContent>
              {report.events.length === 0 ? (
                <div className="text-center py-8 text-gray-500">
                  No proctoring events recorded
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
                    {report.events.map((event) => (
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
