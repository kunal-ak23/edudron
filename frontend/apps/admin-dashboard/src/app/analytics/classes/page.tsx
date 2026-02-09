'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, ArrowLeft, GraduationCap } from 'lucide-react'
import { classesApi, institutesApi } from '@/lib/api'
import type { Class, Institute } from '@kunal-ak23/edudron-shared-utils'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

export default function ClassAnalyticsIndexPage() {
  const router = useRouter()
  const { isAuthenticated } = useAuth()
  const [classes, setClasses] = useState<Class[]>([])
  const [institutes, setInstitutes] = useState<Map<string, Institute>>(new Map())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
      return
    }
    const loadData = async () => {
      try {
        setLoading(true)
        const institutesData = await institutesApi.getActiveInstitutes().catch(() => [])
        const instituteMap = new Map<string, Institute>()
        institutesData.forEach((inst) => instituteMap.set(inst.id, inst))
        setInstitutes(instituteMap)

        const allClasses: Class[] = []
        for (const institute of institutesData) {
          try {
            const instituteClasses = await classesApi.listClassesByInstitute(institute.id)
            allClasses.push(...instituteClasses.filter((c) => c.isActive))
          } catch (err) {
            // Skip institute on error
          }
        }
        setClasses(allClasses)
      } catch (error) {
        // Failed to load data
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
          <h1 className="text-3xl font-bold">Class Analytics</h1>
          <p className="text-gray-600 mt-2">
            Select a class to compare sections and view aggregated analytics
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <GraduationCap className="h-5 w-5 text-muted-foreground" />
            <CardTitle>Classes</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          {classes.length === 0 ? (
            <p className="text-muted-foreground py-4">No active classes available.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Class name</TableHead>
                  <TableHead>Institute</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {classes.map((classItem) => (
                  <TableRow key={classItem.id}>
                    <TableCell className="font-medium">{classItem.name}</TableCell>
                    <TableCell>
                      {institutes.get(classItem.instituteId)?.name ?? '—'}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button variant="outline" size="sm" asChild>
                        <Link href={`/analytics/classes/${classItem.id}`}>
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
