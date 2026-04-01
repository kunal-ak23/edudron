'use client'

import { useEffect, useState, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Textarea } from '@/components/ui/textarea'
import {
  Award,
  Download,
  Shield,
  Loader2,
  Upload,
  AlertTriangle,
  X,
  Lock,
} from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import {
  certificatesApi,
  coursesApi,
  sectionsApi,
} from '@/lib/api'
import { useCertificatesFeature } from '@/hooks/useCertificatesFeature'

export const dynamic = 'force-dynamic'

interface SelectOption {
  id: string
  name: string
}

interface StudentRow {
  name: string
  email: string
}

interface Certificate {
  id: string
  studentName?: string
  studentEmail?: string
  courseName?: string
  credentialId: string
  issuedAt: string
  revoked: boolean
  revokedReason?: string
}

interface CertificateTemplate {
  id: string
  name: string
  description?: string
  isDefault: boolean
}

export default function CertificatesPage() {
  const { toast } = useToast()
  const { enabled: certificatesEnabled, loading: featureLoading } = useCertificatesFeature()

  // Shared data
  const [courses, setCourses] = useState<SelectOption[]>([])
  const [sections, setSections] = useState<SelectOption[]>([])
  const [templates, setTemplates] = useState<CertificateTemplate[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)

  // Generate tab state
  const [genCourseId, setGenCourseId] = useState('')
  const [genSectionId, setGenSectionId] = useState('')
  const [genTemplateId, setGenTemplateId] = useState('')
  const [students, setStudents] = useState<StudentRow[]>([])
  const [generating, setGenerating] = useState(false)

  // Issued tab state
  const [filterCourseId, setFilterCourseId] = useState('')
  const [filterSectionId, setFilterSectionId] = useState('')
  const [certificates, setCertificates] = useState<Certificate[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [currentPage, setCurrentPage] = useState(0)
  const [loadingCerts, setLoadingCerts] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [downloadingZip, setDownloadingZip] = useState(false)

  // Revoke dialog
  const [revokeTarget, setRevokeTarget] = useState<Certificate | null>(null)
  const [revokeReason, setRevokeReason] = useState('')
  const [revoking, setRevoking] = useState(false)

  // Load shared options
  const loadOptions = useCallback(async () => {
    setLoadingOptions(true)
    try {
      const [coursesRes, templatesRes] = await Promise.all([
        coursesApi.listCourses().catch(() => []),
        certificatesApi.listTemplates().catch(() => []),
      ])
      const sectionsRes: any[] = [] // Sections loaded dynamically when class is selected

      const normalize = (data: any): SelectOption[] => {
        const arr = Array.isArray(data)
          ? data
          : data?.content && Array.isArray(data.content)
            ? data.content
            : data?.data && Array.isArray(data.data)
              ? data.data
              : []
        return arr.map((item: any) => ({
          id: item.id,
          name: item.name || item.title || item.id,
        }))
      }

      setCourses(normalize(coursesRes))
      setSections(normalize(sectionsRes))

      const tmpl = Array.isArray(templatesRes) ? templatesRes : []
      setTemplates(tmpl)
      // Auto-select default template
      const def = tmpl.find((t) => t.isDefault)
      if (def) setGenTemplateId(def.id)
    } catch {
      toast({
        title: 'Error',
        description: 'Failed to load options',
        variant: 'destructive',
      })
    } finally {
      setLoadingOptions(false)
    }
  }, [toast])

  useEffect(() => {
    loadOptions()
  }, [loadOptions])

  // Load certificates for "Issued" tab
  const loadCertificates = useCallback(async () => {
    setLoadingCerts(true)
    try {
      const params: Record<string, string | number> = { page: currentPage, size: 20 }
      if (filterCourseId) params.courseId = filterCourseId
      if (filterSectionId) params.sectionId = filterSectionId

      const result = await certificatesApi.list(params as any)
      setCertificates(result.content as Certificate[])
      setTotalElements(result.totalElements)
      setTotalPages(result.totalPages)
    } catch {
      toast({
        title: 'Error',
        description: 'Failed to load certificates',
        variant: 'destructive',
      })
    } finally {
      setLoadingCerts(false)
    }
  }, [filterCourseId, filterSectionId, currentPage, toast])

  useEffect(() => {
    loadCertificates()
  }, [loadCertificates])

  // CSV upload handler
  const handleCsvUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (event) => {
      const text = event.target?.result as string
      const lines = text.trim().split('\n')
      const parsed: StudentRow[] = []
      for (let i = 1; i < lines.length; i++) {
        const [name, email] = lines[i].split(',').map((s) => s.trim())
        if (name && email) parsed.push({ name, email })
      }
      setStudents(parsed)
      if (parsed.length === 0) {
        toast({
          title: 'Empty CSV',
          description: 'No valid student rows found. Expected columns: name, email.',
          variant: 'destructive',
        })
      }
    }
    reader.readAsText(file)
  }

  // Generate certificates
  const handleGenerate = useCallback(async () => {
    if (!genCourseId || !genTemplateId || students.length === 0) return
    setGenerating(true)
    try {
      const result = await certificatesApi.generate({
        courseId: genCourseId,
        sectionId: genSectionId && genSectionId !== '__all__' ? genSectionId : undefined,
        templateId: genTemplateId,
        students,
      })
      const count = Array.isArray(result) ? result.length : 0
      toast({
        title: 'Certificates generated',
        description: `${count} certificate(s) created successfully`,
      })
      setStudents([])
      // Refresh issued list
      loadCertificates()
    } catch {
      toast({
        title: 'Generation failed',
        description: 'Could not generate certificates. Please try again.',
        variant: 'destructive',
      })
    } finally {
      setGenerating(false)
    }
  }, [genCourseId, genSectionId, genTemplateId, students, toast, loadCertificates])

  // Download single certificate
  const handleDownload = useCallback(
    async (cert: Certificate) => {
      setDownloadingId(cert.id)
      try {
        const blob = await certificatesApi.downloadPdf(cert.id)
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `certificate-${cert.credentialId}.pdf`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        window.URL.revokeObjectURL(url)
      } catch {
        toast({
          title: 'Download failed',
          description: 'Could not download the certificate PDF.',
          variant: 'destructive',
        })
      } finally {
        setDownloadingId(null)
      }
    },
    [toast]
  )

  // Download all as ZIP
  const handleDownloadZip = useCallback(async () => {
    if (!filterSectionId || !filterCourseId) {
      toast({
        title: 'Filters required',
        description: 'Select both a course and a section to download as ZIP.',
        variant: 'destructive',
      })
      return
    }
    setDownloadingZip(true)
    try {
      const blob = await certificatesApi.downloadAllAsZip(filterSectionId, filterCourseId)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `certificates-${new Date().toISOString().split('T')[0]}.zip`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
    } catch {
      toast({
        title: 'Download failed',
        description: 'Could not download the ZIP archive.',
        variant: 'destructive',
      })
    } finally {
      setDownloadingZip(false)
    }
  }, [filterSectionId, filterCourseId, toast])

  // Revoke certificate
  const handleRevoke = useCallback(async () => {
    if (!revokeTarget || !revokeReason.trim()) return
    setRevoking(true)
    try {
      await certificatesApi.revoke(revokeTarget.id, revokeReason.trim())
      toast({
        title: 'Certificate revoked',
        description: `Credential ${revokeTarget.credentialId} has been revoked.`,
      })
      setRevokeTarget(null)
      setRevokeReason('')
      loadCertificates()
    } catch {
      toast({
        title: 'Revocation failed',
        description: 'Could not revoke the certificate. Please try again.',
        variant: 'destructive',
      })
    } finally {
      setRevoking(false)
    }
  }, [revokeTarget, revokeReason, toast, loadCertificates])

  if (featureLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!certificatesEnabled) {
    return (
      <div className="space-y-6">
        <div>
          <div className="flex items-center gap-2">
            <Award className="h-7 w-7 text-primary" />
            <h1 className="text-3xl font-bold">Certificates</h1>
          </div>
        </div>
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16">
            <Lock className="h-12 w-12 text-muted-foreground mb-4" />
            <h2 className="text-xl font-semibold text-gray-700 mb-2">Premium Feature</h2>
            <p className="text-muted-foreground text-center max-w-md">
              Certificate generation is a premium feature. Contact your system administrator to enable it.
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2">
          <Award className="h-7 w-7 text-primary" />
          <h1 className="text-3xl font-bold">Certificates</h1>
        </div>
        <p className="text-gray-600 mt-1">
          Generate and manage student certificates.
        </p>
      </div>

      <Tabs defaultValue="generate">
        <TabsList>
          <TabsTrigger value="generate" className="cursor-pointer">
            Generate Certificates
          </TabsTrigger>
          <TabsTrigger value="issued" className="cursor-pointer">
            Issued Certificates
          </TabsTrigger>
        </TabsList>

        {/* ===== Generate Tab ===== */}
        <TabsContent value="generate">
          <Card>
            <CardHeader>
              <CardTitle>Generate Certificates</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* Selectors */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <Label htmlFor="gen-course">Course</Label>
                  {loadingOptions ? (
                    <div className="flex items-center gap-2 mt-2 text-sm text-muted-foreground">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Loading...
                    </div>
                  ) : (
                    <Select value={genCourseId} onValueChange={setGenCourseId}>
                      <SelectTrigger id="gen-course" className="mt-1 cursor-pointer">
                        <SelectValue placeholder="Select course..." />
                      </SelectTrigger>
                      <SelectContent>
                        {courses.map((c) => (
                          <SelectItem key={c.id} value={c.id} className="cursor-pointer">
                            {c.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                </div>

                <div>
                  <Label htmlFor="gen-section">Section (optional)</Label>
                  <Select value={genSectionId} onValueChange={setGenSectionId}>
                    <SelectTrigger id="gen-section" className="mt-1 cursor-pointer">
                      <SelectValue placeholder="All sections" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__" className="cursor-pointer">
                        All sections
                      </SelectItem>
                      {sections.map((s) => (
                        <SelectItem key={s.id} value={s.id} className="cursor-pointer">
                          {s.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label htmlFor="gen-template">Template</Label>
                  <Select value={genTemplateId} onValueChange={setGenTemplateId}>
                    <SelectTrigger id="gen-template" className="mt-1 cursor-pointer">
                      <SelectValue placeholder="Select template..." />
                    </SelectTrigger>
                    <SelectContent>
                      {templates.map((t) => (
                        <SelectItem key={t.id} value={t.id} className="cursor-pointer">
                          {t.name}
                          {t.isDefault ? ' (Default)' : ''}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              {/* CSV Upload */}
              <div>
                <Label htmlFor="csv-upload">Upload Student CSV</Label>
                <p className="text-xs text-muted-foreground mb-2">
                  CSV with header row: name, email
                </p>
                <div className="flex items-center gap-4">
                  <label
                    htmlFor="csv-upload"
                    className="flex items-center gap-2 px-4 py-2 border border-dashed rounded-md cursor-pointer hover:bg-muted/50 transition-colors duration-200"
                  >
                    <Upload className="h-4 w-4" />
                    <span className="text-sm">Choose file</span>
                  </label>
                  <Input
                    id="csv-upload"
                    type="file"
                    accept=".csv"
                    className="hidden"
                    onChange={handleCsvUpload}
                  />
                  {students.length > 0 && (
                    <div className="flex items-center gap-2">
                      <Badge variant="outline">{students.length} students loaded</Badge>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setStudents([])}
                        className="cursor-pointer"
                        aria-label="Clear uploaded students"
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                </div>
              </div>

              {/* Student preview table */}
              {students.length > 0 && (
                <div className="border rounded-lg overflow-hidden max-h-64 overflow-y-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>#</TableHead>
                        <TableHead>Name</TableHead>
                        <TableHead>Email</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {students.slice(0, 50).map((s, i) => (
                        <TableRow key={i}>
                          <TableCell className="text-muted-foreground">{i + 1}</TableCell>
                          <TableCell>{s.name}</TableCell>
                          <TableCell>{s.email}</TableCell>
                        </TableRow>
                      ))}
                      {students.length > 50 && (
                        <TableRow>
                          <TableCell colSpan={3} className="text-center text-muted-foreground text-sm">
                            ... and {students.length - 50} more
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </div>
              )}

              {/* Bulk limit warning */}
              {students.length > 150 && (
                <Alert>
                  <AlertTriangle className="h-4 w-4" />
                  <AlertTitle>Large batch</AlertTitle>
                  <AlertDescription>
                    You are about to generate {students.length} certificates. This
                    may take a while. Consider splitting into smaller batches.
                  </AlertDescription>
                </Alert>
              )}

              {/* Generate button */}
              <Button
                onClick={handleGenerate}
                disabled={
                  !genCourseId ||
                  !genTemplateId ||
                  students.length === 0 ||
                  generating
                }
                className="cursor-pointer transition-colors duration-200"
              >
                {generating ? (
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                ) : (
                  <Award className="h-4 w-4 mr-2" />
                )}
                {generating
                  ? 'Generating...'
                  : `Generate ${students.length > 0 ? students.length : ''} Certificate${students.length !== 1 ? 's' : ''}`}
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ===== Issued Tab ===== */}
        <TabsContent value="issued">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between flex-wrap gap-2">
                <CardTitle>Issued Certificates</CardTitle>
                <Button
                  variant="outline"
                  onClick={handleDownloadZip}
                  disabled={downloadingZip || !filterCourseId || !filterSectionId}
                  className="cursor-pointer transition-colors duration-200"
                  aria-label="Download all certificates as ZIP"
                >
                  {downloadingZip ? (
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  ) : (
                    <Download className="h-4 w-4 mr-2" />
                  )}
                  Download All as ZIP
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Filters */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <Label htmlFor="filter-course">Course</Label>
                  <Select
                    value={filterCourseId}
                    onValueChange={(v) => {
                      setFilterCourseId(v === '__all__' ? '' : v)
                      setCurrentPage(0)
                    }}
                  >
                    <SelectTrigger id="filter-course" className="mt-1 cursor-pointer">
                      <SelectValue placeholder="All courses" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__" className="cursor-pointer">
                        All courses
                      </SelectItem>
                      {courses.map((c) => (
                        <SelectItem key={c.id} value={c.id} className="cursor-pointer">
                          {c.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <Label htmlFor="filter-section">Section</Label>
                  <Select
                    value={filterSectionId}
                    onValueChange={(v) => {
                      setFilterSectionId(v === '__all__' ? '' : v)
                      setCurrentPage(0)
                    }}
                  >
                    <SelectTrigger id="filter-section" className="mt-1 cursor-pointer">
                      <SelectValue placeholder="All sections" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__" className="cursor-pointer">
                        All sections
                      </SelectItem>
                      {sections.map((s) => (
                        <SelectItem key={s.id} value={s.id} className="cursor-pointer">
                          {s.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              {/* Table */}
              {loadingCerts ? (
                <div className="flex items-center justify-center h-32">
                  <Loader2 className="h-8 w-8 animate-spin" />
                </div>
              ) : certificates.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">
                  No certificates found
                </div>
              ) : (
                <>
                  <div className="border rounded-lg overflow-hidden">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Student</TableHead>
                          <TableHead>Course</TableHead>
                          <TableHead>Credential ID</TableHead>
                          <TableHead>Issued Date</TableHead>
                          <TableHead>Status</TableHead>
                          <TableHead className="text-right">Actions</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {certificates.map((cert) => (
                          <TableRow key={cert.id}>
                            <TableCell>
                              <div>
                                <div className="font-medium">
                                  {cert.studentName || 'Unknown'}
                                </div>
                                <div className="text-xs text-muted-foreground">
                                  {cert.studentEmail}
                                </div>
                              </div>
                            </TableCell>
                            <TableCell>{cert.courseName || '-'}</TableCell>
                            <TableCell>
                              <code className="font-mono text-xs bg-muted px-1.5 py-0.5 rounded">
                                {cert.credentialId}
                              </code>
                            </TableCell>
                            <TableCell className="text-sm">
                              {new Date(cert.issuedAt).toLocaleDateString()}
                            </TableCell>
                            <TableCell>
                              {cert.revoked ? (
                                <Badge variant="destructive">Revoked</Badge>
                              ) : (
                                <Badge variant="default">Valid</Badge>
                              )}
                            </TableCell>
                            <TableCell className="text-right">
                              <div className="flex items-center justify-end gap-1">
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => handleDownload(cert)}
                                  disabled={downloadingId === cert.id}
                                  className="cursor-pointer transition-colors duration-200"
                                  aria-label="Download certificate"
                                >
                                  {downloadingId === cert.id ? (
                                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                  ) : (
                                    <Download className="h-3.5 w-3.5" />
                                  )}
                                </Button>
                                {!cert.revoked && (
                                  <Button
                                    variant="destructive"
                                    size="sm"
                                    onClick={() => setRevokeTarget(cert)}
                                    className="cursor-pointer transition-colors duration-200"
                                    aria-label="Revoke certificate"
                                  >
                                    <Shield className="h-3.5 w-3.5" />
                                  </Button>
                                )}
                              </div>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>

                  {/* Pagination */}
                  {totalPages > 1 && (
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">
                        {totalElements} certificate{totalElements !== 1 ? 's' : ''} total
                      </span>
                      <div className="flex gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={currentPage === 0}
                          onClick={() => setCurrentPage((p) => p - 1)}
                          className="cursor-pointer transition-colors duration-200"
                        >
                          Previous
                        </Button>
                        <span className="flex items-center px-2 text-muted-foreground">
                          Page {currentPage + 1} of {totalPages}
                        </span>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={currentPage + 1 >= totalPages}
                          onClick={() => setCurrentPage((p) => p + 1)}
                          className="cursor-pointer transition-colors duration-200"
                        >
                          Next
                        </Button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Revoke Confirmation Dialog */}
      <Dialog
        open={revokeTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRevokeTarget(null)
            setRevokeReason('')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Revoke Certificate</DialogTitle>
            <DialogDescription>
              This will permanently revoke credential{' '}
              <code className="font-mono text-xs bg-muted px-1 py-0.5 rounded">
                {revokeTarget?.credentialId}
              </code>
              . This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="revoke-reason">Reason for revocation</Label>
            <Textarea
              id="revoke-reason"
              placeholder="Enter the reason..."
              value={revokeReason}
              onChange={(e) => setRevokeReason(e.target.value)}
              rows={3}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setRevokeTarget(null)
                setRevokeReason('')
              }}
              className="cursor-pointer transition-colors duration-200"
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleRevoke}
              disabled={!revokeReason.trim() || revoking}
              className="cursor-pointer transition-colors duration-200"
            >
              {revoking ? (
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              ) : (
                <Shield className="h-4 w-4 mr-2" />
              )}
              {revoking ? 'Revoking...' : 'Revoke'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
