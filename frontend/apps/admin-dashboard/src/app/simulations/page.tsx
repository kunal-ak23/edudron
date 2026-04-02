'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
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
import { Gamepad2, Sparkles, Loader2, Upload, ChevronLeft, ChevronRight } from 'lucide-react'
import { simulationsApi } from '@/lib/api'
import type { SimulationDTO } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700 border-gray-300',
  GENERATING: 'bg-yellow-100 text-yellow-700 border-yellow-300',
  REVIEW: 'bg-blue-100 text-blue-700 border-blue-300',
  PUBLISHED: 'bg-green-100 text-green-700 border-green-300',
  ARCHIVED: 'bg-gray-100 text-gray-500 border-gray-300',
}

export default function SimulationsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [simulations, setSimulations] = useState<SimulationDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [importing, setImporting] = useState(false)
  const pageSize = 20

  const loadSimulations = useCallback(async () => {
    setLoading(true)
    try {
      const status = statusFilter === 'ALL' ? undefined : statusFilter
      const result = await simulationsApi.listSimulations(page, pageSize, status)
      setSimulations(result.content)
      setTotalPages(result.totalPages)
      setTotalElements(result.totalElements)
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to load simulations',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
    }
  }, [page, statusFilter, toast])

  useEffect(() => {
    loadSimulations()
  }, [loadSimulations])

  // Reset to page 0 when filter changes
  useEffect(() => {
    setPage(0)
  }, [statusFilter])

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setImporting(true)
    try {
      const text = await file.text()
      const data = JSON.parse(text)
      await simulationsApi.importSimulation(data)
      toast({
        title: 'Simulation Imported',
        description: 'The simulation has been imported successfully.',
      })
      loadSimulations()
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Import Failed',
        description: extractErrorMessage(error) || 'Failed to import simulation. Check the JSON format.',
      })
    } finally {
      setImporting(false)
      // Reset file input so same file can be re-selected
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    }
  }

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  }

  return (
    <div className="space-y-3">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div />
        <div className="flex gap-2">
          <input
            ref={fileInputRef}
            type="file"
            accept=".json"
            onChange={handleImport}
            className="hidden"
          />
          <Button
            variant="outline"
            onClick={() => fileInputRef.current?.click()}
            disabled={importing}
          >
            {importing ? (
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
            ) : (
              <Upload className="h-4 w-4 mr-2" />
            )}
            Import
          </Button>
          <Button onClick={() => router.push('/simulations/generate')}>
            <Sparkles className="h-4 w-4 mr-2" />
            Generate New Simulation
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="w-48">
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger>
              <SelectValue placeholder="Filter by status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Statuses</SelectItem>
              <SelectItem value="DRAFT">Draft</SelectItem>
              <SelectItem value="GENERATING">Generating</SelectItem>
              <SelectItem value="REVIEW">Review</SelectItem>
              <SelectItem value="PUBLISHED">Published</SelectItem>
              <SelectItem value="ARCHIVED">Archived</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <p className="text-sm text-muted-foreground">
          {totalElements} simulation{totalElements !== 1 ? 's' : ''} found
        </p>
      </div>

      {/* Table */}
      {loading ? (
        <Card>
          <CardContent className="py-12">
            <div className="text-center">
              <Loader2 className="h-8 w-8 animate-spin text-primary mx-auto" />
            </div>
          </CardContent>
        </Card>
      ) : simulations.length === 0 ? (
        <Card>
          <CardContent className="text-center py-12">
            <Gamepad2 className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-2 text-sm font-medium">No simulations</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Get started by generating a new simulation.
            </p>
            <div className="mt-6">
              <Button onClick={() => router.push('/simulations/generate')}>
                <Sparkles className="h-4 w-4 mr-2" />
                Generate New Simulation
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Concept</TableHead>
                  <TableHead>Subject</TableHead>
                  <TableHead>Course</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-center">Format</TableHead>
                  <TableHead className="text-center">Total Plays</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {simulations.map((sim) => (
                  <TableRow
                    key={sim.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => router.push(`/simulations/${sim.id}`)}
                  >
                    <TableCell className="font-medium max-w-[250px] truncate">
                      {sim.title || 'Untitled'}
                    </TableCell>
                    <TableCell className="max-w-[200px] truncate">
                      {sim.concept}
                    </TableCell>
                    <TableCell>{sim.subject}</TableCell>
                    <TableCell className="max-w-[180px] truncate text-sm text-muted-foreground">
                      {sim.courseName || '—'}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={STATUS_COLORS[sim.status] || ''}
                      >
                        {sim.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-center text-sm text-muted-foreground">
                      {sim.targetYears} yrs × {sim.decisionsPerYear} dec
                    </TableCell>
                    <TableCell className="text-center">
                      {sim.totalPlays}
                    </TableCell>
                    <TableCell>{formatDate(sim.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-sm text-muted-foreground">
                Page {page + 1} of {totalPages}
              </p>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  <ChevronLeft className="h-4 w-4 mr-1" />
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                >
                  Next
                  <ChevronRight className="h-4 w-4 ml-1" />
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
