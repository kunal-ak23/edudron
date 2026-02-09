'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, ArrowLeft, Layers } from 'lucide-react'
import { enrollmentsApi } from '@/lib/api'
import type { Batch } from '@kunal-ak23/edudron-shared-utils'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

export default function SectionAnalyticsIndexPage() {
  const router = useRouter()
  const { isAuthenticated } = useAuth()
  const [sections, setSections] = useState<Batch[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
      return
    }
    const loadData = async () => {
      try {
        setLoading(true)
        const data = await enrollmentsApi.listSections().catch(() => [])
        setSections(data.filter((s) => s.isActive))
      } catch (error) {
        // Failed to load sections
      } finally {
        setLoading(false)
      }
    }
    loadData()
  }, [isAuthenticated, router])

  if (loading) {
    return (
      <div className="container mx-auto py-8 px-4">
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto py-8 px-4">
      <div className="mb-6 flex items-center gap-4">
        <Button variant="ghost" onClick={() => router.push('/analytics')}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        <div>
          <h1 className="text-3xl font-bold">Section Analytics</h1>
          <p className="text-gray-600 mt-2">
            Select a section to view engagement analytics across its courses
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Layers className="h-5 w-5 text-muted-foreground" />
            <CardTitle>Sections</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          {sections.length === 0 ? (
            <p className="text-muted-foreground py-4">No active sections available.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Section name</TableHead>
                  <TableHead>Students</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sections.map((section) => (
                  <TableRow key={section.id}>
                    <TableCell className="font-medium">{section.name}</TableCell>
                    <TableCell>
                      {section.studentCount ?? section.enrolledCount ?? '—'}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button variant="outline" size="sm" asChild>
                        <Link href={`/analytics/sections/${section.id}`}>
                          View analytics
                        </Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
