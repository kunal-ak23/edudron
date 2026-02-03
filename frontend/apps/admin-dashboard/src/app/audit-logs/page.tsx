'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
import { Loader2, ChevronLeft, ChevronRight, ScrollText, ChevronDown, ChevronUp } from 'lucide-react'
import { apiClient } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

interface AuditLogEntry {
  id: string
  clientId: string
  actor: string
  action: string
  entity: string
  entityId: string
  meta: string | null
  createdAt: string
}

interface PaginatedAuditLogResponse {
  content: AuditLogEntry[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

const ENTITY_OPTIONS = [
  'All',
  'User',
  'Client',
  'TenantBranding',
  'TenantFeature',
  'Course',
  'Section',
  'Lecture',
  'Assessment',
  'QuestionBank',
  'Enrollment',
  'Institute',
  'Batch',
  'Class',
  'Payment',
  'SubscriptionPlan',
  'Subscription',
]

const ACTION_OPTIONS = ['All', 'CREATE', 'UPDATE', 'DELETE', 'PUBLISH', 'UNPUBLISH', 'TRANSFER']

function formatDateTime(iso: string): string {
  try {
    const d = new Date(iso)
    return d.toLocaleString(undefined, {
      dateStyle: 'short',
      timeStyle: 'medium',
    })
  } catch {
    return iso
  }
}

function truncateMeta(meta: string | null, maxLen = 60): string {
  if (!meta) return '—'
  const trimmed = meta.trim()
  if (trimmed.length <= maxLen) return trimmed
  return trimmed.slice(0, maxLen) + '…'
}

export default function AuditLogsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const { user } = useAuth()
  const [logs, setLogs] = useState<AuditLogEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [entityFilter, setEntityFilter] = useState<string>('All')
  const [actionFilter, setActionFilter] = useState<string>('All')
  const [actorQuery, setActorQuery] = useState('')
  const [debouncedActor, setDebouncedActor] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [first, setFirst] = useState(true)
  const [last, setLast] = useState(true)

  const isSystemAdmin = user?.role === 'SYSTEM_ADMIN'

  const loadAuditLogs = useCallback(async () => {
    try {
      setLoading(true)
      const params = new URLSearchParams()
      params.append('page', currentPage.toString())
      params.append('size', Math.min(pageSize, 100).toString())
      if (entityFilter && entityFilter !== 'All') params.append('entity', entityFilter)
      if (actionFilter && actionFilter !== 'All') params.append('action', actionFilter)
      if (debouncedActor.trim()) params.append('actor', debouncedActor.trim())
      if (fromDate) params.append('from', new Date(fromDate + 'T00:00:00.000Z').toISOString())
      if (toDate) params.append('to', new Date(toDate + 'T23:59:59.999Z').toISOString())

      const response = await apiClient.get<PaginatedAuditLogResponse>(
        `/idp/audit-logs?${params.toString()}`
      )
      const data = response.data || {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: pageSize,
        first: true,
        last: true,
      }
      setLogs(data.content || [])
      setTotalElements(data.totalElements ?? 0)
      setTotalPages(data.totalPages ?? 0)
      setFirst(data.first ?? true)
      setLast(data.last ?? true)
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load audit logs',
        description: errorMessage,
      })
      setLogs([])
      setTotalElements(0)
      setTotalPages(0)
    } finally {
      setLoading(false)
    }
  }, [toast, currentPage, pageSize, entityFilter, actionFilter, debouncedActor, fromDate, toDate])

  useEffect(() => {
    const t = setTimeout(() => setDebouncedActor(actorQuery), 400)
    return () => clearTimeout(t)
  }, [actorQuery])

  useEffect(() => {
    loadAuditLogs()
  }, [loadAuditLogs])

  // SYSTEM_ADMIN only: redirect others
  useEffect(() => {
    if (user && !isSystemAdmin) {
      router.push('/unauthorized')
      return
    }
    const tenantId = typeof window !== 'undefined'
      ? (localStorage.getItem('clientId') || localStorage.getItem('selectedTenantId') || localStorage.getItem('tenant_id'))
      : null
    if (user && isSystemAdmin && (!tenantId || tenantId === 'PENDING_TENANT_SELECTION' || tenantId === 'SYSTEM')) {
      router.push('/select-tenant')
    }
  }, [user, isSystemAdmin, router])

  const handleApplyFilters = () => {
    setCurrentPage(0)
  }

  const handleClearFilters = () => {
    setEntityFilter('All')
    setActionFilter('All')
    setActorQuery('')
    setFromDate('')
    setToDate('')
    setCurrentPage(0)
  }

  // Reset to first page when filters or page size change
  useEffect(() => {
    setCurrentPage(0)
  }, [entityFilter, actionFilter, debouncedActor, fromDate, toDate, pageSize])

  const startItem = totalElements === 0 ? 0 : currentPage * pageSize + 1
  const endItem = Math.min((currentPage + 1) * pageSize, totalElements)

  if (!user) return null
  if (!isSystemAdmin) return null

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center gap-2">
        <ScrollText className="h-8 w-8 text-primary" />
        <h1 className="text-2xl font-semibold">Audit logs</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
            <div className="space-y-2">
              <Label>Entity</Label>
              <Select value={entityFilter} onValueChange={setEntityFilter}>
                <SelectTrigger>
                  <SelectValue placeholder="All" />
                </SelectTrigger>
                <SelectContent>
                  {ENTITY_OPTIONS.map((opt) => (
                    <SelectItem key={opt} value={opt}>
                      {opt}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Action</Label>
              <Select value={actionFilter} onValueChange={setActionFilter}>
                <SelectTrigger>
                  <SelectValue placeholder="All" />
                </SelectTrigger>
                <SelectContent>
                  {ACTION_OPTIONS.map((opt) => (
                    <SelectItem key={opt} value={opt}>
                      {opt}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Actor</Label>
              <Input
                placeholder="Search by actor"
                value={actorQuery}
                onChange={(e) => setActorQuery(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>From date</Label>
              <Input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>To date</Label>
              <Input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
              />
            </div>
          </div>
          <div className="flex gap-2">
            <Button onClick={handleApplyFilters}>Apply</Button>
            <Button variant="outline" onClick={handleClearFilters}>
              Clear
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : (
            <>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Date / time</TableHead>
                      <TableHead>Actor</TableHead>
                      <TableHead>Action</TableHead>
                      <TableHead>Entity</TableHead>
                      <TableHead>Entity ID</TableHead>
                      <TableHead>Meta</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {logs.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={6} className="text-center text-muted-foreground py-8">
                          No audit logs found.
                        </TableCell>
                      </TableRow>
                    ) : (
                      logs.map((entry) => (
                        <TableRow key={entry.id}>
                          <TableCell className="whitespace-nowrap">
                            {formatDateTime(entry.createdAt)}
                          </TableCell>
                          <TableCell className="font-mono text-sm">{entry.actor || '—'}</TableCell>
                          <TableCell>
                            <Badge variant="secondary">{entry.action}</Badge>
                          </TableCell>
                          <TableCell>{entry.entity || '—'}</TableCell>
                          <TableCell className="font-mono text-sm max-w-[120px] truncate" title={entry.entityId}>
                            {entry.entityId || '—'}
                          </TableCell>
                          <TableCell>
                            <MetaCell meta={entry.meta} />
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </div>

              <div className="flex items-center justify-between mt-4">
                <p className="text-sm text-muted-foreground">
                  Showing {startItem}–{endItem} of {totalElements}
                </p>
                <div className="flex items-center gap-2">
                  <Select
                    value={pageSize.toString()}
                    onValueChange={(v) => {
                      setPageSize(Number(v))
                      setCurrentPage(0)
                    }}
                  >
                    <SelectTrigger className="w-[70px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="10">10</SelectItem>
                      <SelectItem value="20">20</SelectItem>
                      <SelectItem value="50">50</SelectItem>
                    </SelectContent>
                  </Select>
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                    disabled={first}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => setCurrentPage((p) => p + 1)}
                    disabled={last}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function MetaCell({ meta }: { meta: string | null }) {
  const [open, setOpen] = useState(false)
  if (!meta || !meta.trim()) return <span className="text-muted-foreground">—</span>
  const truncated = truncateMeta(meta, 50)
  const hasMore = meta.length > 50
  if (!hasMore) return <span className="text-xs font-mono break-all">{meta}</span>
  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <span className="text-xs font-mono break-all">{truncated}</span>
      <CollapsibleTrigger asChild>
        <Button variant="ghost" size="sm" className="h-6 px-1 ml-1">
          {open ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
        </Button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <pre className="text-xs font-mono break-all mt-1 p-2 bg-muted rounded max-h-32 overflow-auto">
          {meta}
        </pre>
      </CollapsibleContent>
    </Collapsible>
  )
}
