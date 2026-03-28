'use client'

import { useEffect, useState, useCallback } from 'react'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import type { Certificate, CertificateVisibility } from '@kunal-ak23/edudron-shared-utils'
import { StudentLayout } from '@/components/StudentLayout'
import { certificatesApi } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import {
  Award,
  Download,
  Loader2,
  ChevronDown,
  ChevronUp,
  FileText,
  Calendar,
  Hash,
} from 'lucide-react'

export const dynamic = 'force-dynamic'

export default function MyCertificatesPage() {
  const { needsTenantSelection } = useAuth()
  const { toast } = useToast()
  const [certificates, setCertificates] = useState<Certificate[]>([])
  const [loading, setLoading] = useState(true)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [updatingVisibility, setUpdatingVisibility] = useState<string | null>(null)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const loadCertificates = useCallback(async () => {
    if (needsTenantSelection) {
      setLoading(false)
      return
    }
    try {
      const data = await certificatesApi.myCertificates()
      setCertificates(data)
    } catch (error) {
      toast({
        title: 'Error',
        description: 'Failed to load certificates.',
        variant: 'destructive',
      })
    } finally {
      setLoading(false)
    }
  }, [needsTenantSelection])

  useEffect(() => {
    if (!needsTenantSelection) {
      loadCertificates()
    }
  }, [loadCertificates])

  const handleDownload = async (cert: Certificate) => {
    setDownloadingId(cert.id)
    try {
      const blob = await certificatesApi.downloadPdf(cert.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `certificate-${cert.credentialId}.pdf`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (error) {
      toast({
        title: 'Download failed',
        description: 'Could not download the certificate PDF.',
        variant: 'destructive',
      })
    } finally {
      setDownloadingId(null)
    }
  }

  const handleVisibilityToggle = async (
    cert: Certificate,
    field: keyof CertificateVisibility,
    value: boolean
  ) => {
    const currentVisibility: CertificateVisibility = cert.visibility || {
      showScores: true,
      showProjectDetails: true,
      showOverallPercentage: true,
      showCourseName: true,
    }
    const updatedVisibility: CertificateVisibility = {
      ...currentVisibility,
      [field]: value,
    }

    setUpdatingVisibility(cert.id)
    try {
      const updated = await certificatesApi.updateVisibility(cert.id, updatedVisibility)
      setCertificates((prev) =>
        prev.map((c) => (c.id === cert.id ? { ...c, visibility: updated.visibility ?? updatedVisibility } : c))
      )
      toast({
        title: 'Updated',
        description: 'Visibility settings saved.',
      })
    } catch (error) {
      toast({
        title: 'Update failed',
        description: 'Could not update visibility settings.',
        variant: 'destructive',
      })
    } finally {
      setUpdatingVisibility(null)
    }
  }

  const formatDate = (dateStr: string) => {
    try {
      return new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    } catch {
      return dateStr
    }
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <main className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-6">
          {/* Page Header */}
          <div className="flex items-center gap-3 mb-6">
            <Award className="h-8 w-8 text-primary-600" />
            <h1 className="text-3xl font-bold text-gray-900">My Certificates</h1>
          </div>

          {/* Loading State */}
          {loading && (
            <div className="flex items-center justify-center py-20">
              <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
            </div>
          )}

          {/* Empty State */}
          {!loading && certificates.length === 0 && (
            <div className="text-center py-20">
              <FileText className="h-16 w-16 text-gray-300 mx-auto mb-4" />
              <h2 className="text-xl font-semibold text-gray-700 mb-2">
                No certificates yet
              </h2>
              <p className="text-gray-500">
                Certificates will appear here once they are issued for your completed courses.
              </p>
            </div>
          )}

          {/* Certificate Cards */}
          {!loading && certificates.length > 0 && (
            <div className="grid gap-4">
              {certificates.map((cert) => {
                const isExpanded = expandedId === cert.id
                const visibility: CertificateVisibility = cert.visibility || {
                  showScores: true,
                  showProjectDetails: true,
                  showOverallPercentage: true,
                  showCourseName: true,
                }

                return (
                  <Card key={cert.id} className="bg-white">
                    <CardHeader className="pb-3">
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <CardTitle className="text-lg font-semibold text-gray-900">
                            {cert.courseName || 'Certificate'}
                          </CardTitle>
                          <div className="flex items-center gap-4 mt-2 text-sm text-gray-500">
                            <span className="flex items-center gap-1.5">
                              <Hash className="h-3.5 w-3.5" />
                              <code className="font-mono text-xs bg-gray-100 px-1.5 py-0.5 rounded">
                                {cert.credentialId}
                              </code>
                            </span>
                            <span className="flex items-center gap-1.5">
                              <Calendar className="h-3.5 w-3.5" />
                              {formatDate(cert.issuedAt)}
                            </span>
                          </div>
                        </div>
                        <Badge
                          variant={cert.revoked ? 'destructive' : 'default'}
                          className={
                            cert.revoked
                              ? ''
                              : 'bg-green-100 text-green-800 border-green-200 hover:bg-green-100'
                          }
                        >
                          {cert.revoked ? 'Revoked' : 'Valid'}
                        </Badge>
                      </div>
                    </CardHeader>
                    <CardContent>
                      <div className="flex items-center gap-3">
                        {!cert.revoked && (
                          <Button
                            variant="outline"
                            size="sm"
                            className="cursor-pointer hover:bg-gray-50 transition-colors"
                            onClick={() => handleDownload(cert)}
                            disabled={downloadingId === cert.id}
                            aria-label={`Download certificate for ${cert.courseName || 'course'}`}
                          >
                            {downloadingId === cert.id ? (
                              <Loader2 className="h-4 w-4 animate-spin mr-2" />
                            ) : (
                              <Download className="h-4 w-4 mr-2" />
                            )}
                            Download PDF
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="sm"
                          className="cursor-pointer hover:bg-gray-50 transition-colors"
                          onClick={() =>
                            setExpandedId(isExpanded ? null : cert.id)
                          }
                          aria-label="Toggle visibility settings"
                        >
                          {isExpanded ? (
                            <ChevronUp className="h-4 w-4 mr-2" />
                          ) : (
                            <ChevronDown className="h-4 w-4 mr-2" />
                          )}
                          Visibility Settings
                        </Button>
                      </div>

                      {/* Expandable Visibility Settings */}
                      {isExpanded && (
                        <div className="mt-4 pt-4 border-t border-gray-100">
                          <p className="text-sm text-gray-600 mb-3">
                            Control what information is visible on your public certificate verification page.
                          </p>
                          <div className="grid gap-3 sm:grid-cols-2">
                            {([
                              { key: 'showScores' as const, label: 'Show Scores' },
                              { key: 'showProjectDetails' as const, label: 'Show Project Details' },
                              { key: 'showOverallPercentage' as const, label: 'Show Overall Percentage' },
                              { key: 'showCourseName' as const, label: 'Show Course Name' },
                            ]).map(({ key, label }) => (
                              <div
                                key={key}
                                className="flex items-center justify-between rounded-lg border border-gray-200 px-4 py-3"
                              >
                                <Label
                                  htmlFor={`${cert.id}-${key}`}
                                  className="text-sm font-medium text-gray-700 cursor-pointer"
                                >
                                  {label}
                                </Label>
                                <Switch
                                  id={`${cert.id}-${key}`}
                                  checked={visibility[key]}
                                  onCheckedChange={(checked) =>
                                    handleVisibilityToggle(cert, key, checked)
                                  }
                                  disabled={updatingVisibility === cert.id}
                                  aria-label={`Toggle ${label}`}
                                />
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          )}
        </main>
      </StudentLayout>
    </ProtectedRoute>
  )
}
