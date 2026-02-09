'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Loader2, GraduationCap, Search, BarChart3, Users, Plus } from 'lucide-react'
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
import Link from 'next/link'

export default function ClassesListPage() {
  const router = useRouter()
  const { isAuthenticated } = useAuth()
  const [classes, setClasses] = useState<Class[]>([])
  const [institutes, setInstitutes] = useState<Map<string, Institute>>(new Map())
  const [loading, setLoading] = useState(true)
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
      return
    }
    loadData()
  }, [isAuthenticated, router])

  const loadData = async () => {
    try {
      setLoading(true)
      
      // Load all institutes first
      const institutesData = await institutesApi.listInstitutes()
      const instituteMap = new Map<string, Institute>()
      institutesData.forEach(inst => instituteMap.set(inst.id, inst))
      setInstitutes(instituteMap)

      // Load classes from all institutes
      const allClasses: Class[] = []
      for (const institute of institutesData) {
        try {
          const instituteClasses = await classesApi.listClassesByInstitute(institute.id)
          allClasses.push(...instituteClasses)
        } catch (err) {
        }
      }
      
      setClasses(allClasses)
    } catch (error) {
    } finally {
      setLoading(false)
    }
  }

  const filteredClasses = classes.filter(classItem => {
    const searchLower = searchTerm.toLowerCase()
    const instituteName = institutes.get(classItem.instituteId)?.name || ''
    return (
      classItem.name.toLowerCase().includes(searchLower) ||
      classItem.code?.toLowerCase().includes(searchLower) ||
      instituteName.toLowerCase().includes(searchLower) ||
      classItem.grade?.toLowerCase().includes(searchLower) ||
      classItem.level?.toLowerCase().includes(searchLower)
    )
  })

  const activeClasses = filteredClasses.filter(c => c.isActive)
  const inactiveClasses = filteredClasses.filter(c => !c.isActive)

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
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-2">
            <GraduationCap className="h-8 w-8" />
            All Classes
          </h1>
          <p className="text-gray-600 mt-2">
            View and manage all classes across institutes
          </p>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Classes</CardTitle>
            <GraduationCap className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{classes.length}</div>
            <p className="text-xs text-muted-foreground">
              Across {institutes.size} institute{institutes.size !== 1 ? 's' : ''}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Active Classes</CardTitle>
            <Users className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">{activeClasses.length}</div>
            <p className="text-xs text-muted-foreground">Currently active</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Inactive Classes</CardTitle>
            <Users className="h-4 w-4 text-gray-400" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-gray-600">{inactiveClasses.length}</div>
            <p className="text-xs text-muted-foreground">Not active</p>
          </CardContent>
        </Card>
      </div>

      {/* Search */}
      <Card className="mb-6">
        <CardContent className="pt-6">
          <div className="flex items-center gap-2">
            <Search className="h-5 w-5 text-gray-400" />
            <Input
              placeholder="Search by class name, code, institute, grade, or level..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="flex-1"
            />
          </div>
        </CardContent>
      </Card>

      {/* Classes Table */}
      <Card>
        <CardHeader>
          <CardTitle>Classes ({filteredClasses.length})</CardTitle>
        </CardHeader>
        <CardContent>
          {filteredClasses.length === 0 ? (
            <div className="text-center py-12">
              <GraduationCap className="mx-auto h-12 w-12 text-gray-400 mb-4" />
              <h3 className="text-lg font-semibold text-gray-900 mb-2">
                {searchTerm ? 'No classes found' : 'No classes yet'}
              </h3>
              <p className="text-gray-500">
                {searchTerm
                  ? 'Try adjusting your search'
                  : 'Classes will appear here once created'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Class Name</TableHead>
                    <TableHead>Code</TableHead>
                    <TableHead>Institute</TableHead>
                    <TableHead>Grade</TableHead>
                    <TableHead>Level</TableHead>
                    <TableHead>Academic Year</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredClasses.map((classItem) => {
                    const institute = institutes.get(classItem.instituteId)
                    return (
                      <TableRow key={classItem.id} className="cursor-pointer hover:bg-gray-50">
                        <TableCell 
                          className="font-medium"
                          onClick={() => router.push(`/classes/${classItem.id}`)}
                        >
                          {classItem.name}
                        </TableCell>
                        <TableCell>
                          <code className="text-xs bg-gray-100 px-2 py-1 rounded">
                            {classItem.code}
                          </code>
                        </TableCell>
                        <TableCell>
                          <Link 
                            href={`/institutes/${classItem.instituteId}`}
                            className="text-blue-600 hover:underline"
                            onClick={(e) => e.stopPropagation()}
                          >
                            {institute?.name || 'Unknown'}
                          </Link>
                        </TableCell>
                        <TableCell>{classItem.grade || '-'}</TableCell>
                        <TableCell>{classItem.level || '-'}</TableCell>
                        <TableCell>{classItem.academicYear || '-'}</TableCell>
                        <TableCell>
                          <Badge variant={classItem.isActive ? 'default' : 'secondary'}>
                            {classItem.isActive ? 'Active' : 'Inactive'}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex items-center justify-end gap-2">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={(e) => {
                                e.stopPropagation()
                                router.push(`/analytics/classes/${classItem.id}`)
                              }}
                            >
                              <BarChart3 className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => router.push(`/classes/${classItem.id}`)}
                            >
                              View
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Quick Tip */}
      <Card className="mt-6 border-blue-200 bg-blue-50">
        <CardContent className="pt-6">
          <div className="flex items-start gap-3">
            <BarChart3 className="h-5 w-5 text-blue-600 mt-0.5" />
            <div>
              <h4 className="font-semibold text-blue-900 mb-1">
                View Class Analytics
              </h4>
              <p className="text-sm text-blue-800">
                Click the chart icon next to any class to view comprehensive analytics 
                including section comparisons, course breakdowns, and engagement metrics.
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
