'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
import { Badge } from '@/components/ui/badge'
import { Loader2, ArrowLeft, Users, Download, LogIn, UserX } from 'lucide-react'
import { apiClient } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'

interface UserReportRow {
  id: string
  email: string
  name: string
  role: string
  active: boolean
  createdAt?: string
  lastLoginAt?: string | null
  classId?: string | null
  className?: string | null
  sectionId?: string | null
  sectionName?: string | null
}

interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export default function UsersReportPage() {
  const router = useRouter()
  const { isAuthenticated } = useAuth()
  const { toast } = useToast()
  const [users, setUsers] = useState<UserReportRow[]>([])
  const [loading, setLoading] = useState(true)
  const [roleFilter, setRoleFilter] = useState<string>('all')

  const loadAllUsers = useCallback(async () => {
    try {
      setLoading(true)
      const all: UserReportRow[] = []
      let page = 0
      const size = 200
      let totalPages = 1

      do {
        const params = new URLSearchParams()
        params.append('page', page.toString())
        params.append('size', size.toString())
        if (roleFilter && roleFilter !== 'all') {
          params.append('role', roleFilter)
        }

        const response = await apiClient.get<PaginatedResponse<UserReportRow>>(
          `/idp/users/paginated?${params.toString()}`
        )
        const data = response.data || (response as unknown as PaginatedResponse<UserReportRow>)
        const content = data.content ?? []
        totalPages = data.totalPages ?? 1
        all.push(...content)
        page++
      } while (page < totalPages)

      setUsers(all)
    } catch (err: unknown) {
      const fallback = await apiClient.get<UserReportRow[]>('/idp/users').catch(() => null)
      if (fallback?.data && Array.isArray(fallback.data)) {
        let list = fallback.data as UserReportRow[]
        if (roleFilter && roleFilter !== 'all') {
          list = list.filter((u) => u.role === roleFilter)
        }
        setUsers(list)
      } else {
        setUsers([])
        toast({
          title: 'Error',
          description: 'Failed to load users. Please try again.',
          variant: 'destructive',
        })
      }
    } finally {
      setLoading(false)
    }
  }, [roleFilter, toast])

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
      return
    }
    loadAllUsers()
  }, [isAuthenticated, router, loadAllUsers])

  const loggedInCount = users.filter((u) => u.lastLoginAt != null).length
  const neverLoggedInCount = users.filter((u) => u.lastLoginAt == null).length

  const students = users.filter((u) => u.role === 'STUDENT')
  const studentsLoggedIn = students.filter((u) => u.lastLoginAt != null).length
  const studentsNeverLoggedIn = students.filter((u) => u.lastLoginAt == null).length

  // Sort by class then section for grouping
  const sortedUsers = [...users].sort((a, b) => {
    const classA = (a.className ?? '').localeCompare(b.className ?? '')
    if (classA !== 0) return classA
    return (a.sectionName ?? '').localeCompare(b.sectionName ?? '')
  })

  const noClassLabel = 'No class'
  const noSectionLabel = 'No section'

  // Overview: breakdown by class (students only)
  const byClass = new Map<string, { total: number; loggedIn: number; neverLoggedIn: number }>()
  for (const u of students) {
    const key = u.className ?? noClassLabel
    const cur = byClass.get(key) ?? { total: 0, loggedIn: 0, neverLoggedIn: 0 }
    cur.total++
    if (u.lastLoginAt != null) cur.loggedIn++
    else cur.neverLoggedIn++
    byClass.set(key, cur)
  }

  // Overview: breakdown by section (students only), grouped by class
  const bySection = new Map<
    string,
    { sectionName: string; className: string; total: number; loggedIn: number; neverLoggedIn: number }
  >()
  for (const u of students) {
    const sectionName = u.sectionName ?? noSectionLabel
    const className = u.className ?? noClassLabel
    const key = `${className}\t${sectionName}`
    const cur = bySection.get(key) ?? {
      sectionName,
      className,
      total: 0,
      loggedIn: 0,
      neverLoggedIn: 0,
    }
    cur.total++
    if (u.lastLoginAt != null) cur.loggedIn++
    else cur.neverLoggedIn++
    bySection.set(key, cur)
  }

  // Build sections grouped by class for overview export
  const sectionsByClass = new Map<
    string,
    { total: number; loggedIn: number; neverLoggedIn: number; sections: { sectionName: string; total: number; loggedIn: number; neverLoggedIn: number }[] }
  >()
  for (const [, stats] of bySection.entries()) {
    const classKey = stats.className
    let classAgg = sectionsByClass.get(classKey)
    if (!classAgg) {
      classAgg = { total: 0, loggedIn: 0, neverLoggedIn: 0, sections: [] }
      sectionsByClass.set(classKey, classAgg)
    }
    classAgg.total += stats.total
    classAgg.loggedIn += stats.loggedIn
    classAgg.neverLoggedIn += stats.neverLoggedIn
    classAgg.sections.push({
      sectionName: stats.sectionName,
      total: stats.total,
      loggedIn: stats.loggedIn,
      neverLoggedIn: stats.neverLoggedIn,
    })
  }

  const exportOverviewToCSV = () => {
    const date = new Date().toISOString().split('T')[0]
    const safe = (s: string) => `"${(s || '').replace(/"/g, '""')}"`
    const rows: string[] = []
    rows.push('Users login report - Overview')
    rows.push(`Generated,${date}`)
    rows.push('')
    rows.push('Summary,Total users,Total students,Logged in (students),Never logged in (students)')
    rows.push(`Count,${users.length},${students.length},${studentsLoggedIn},${studentsNeverLoggedIn}`)
    rows.push('')
    rows.push('By class,Class,Total,Logged in,Never logged in')
    for (const [className, stats] of Array.from(byClass.entries()).sort((a, b) => a[0].localeCompare(b[0]))) {
      rows.push([safe(''), safe(className), stats.total, stats.loggedIn, stats.neverLoggedIn].join(','))
    }
    rows.push('')
    rows.push('By section (grouped by class),Class,Section,Total,Logged in,Never logged in')
    for (const className of Array.from(sectionsByClass.keys()).sort()) {
      const classAgg = sectionsByClass.get(className)!
      rows.push([safe(className), safe(''), '', '', ''].join(','))
      for (const sec of classAgg.sections.sort((a, b) => a.sectionName.localeCompare(b.sectionName))) {
        rows.push([safe(''), safe(sec.sectionName), sec.total, sec.loggedIn, sec.neverLoggedIn].join(','))
      }
      rows.push([safe(''), safe('Subtotal'), classAgg.total, classAgg.loggedIn, classAgg.neverLoggedIn].join(','))
      rows.push('')
    }
    const csvContent = rows.join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `users-login-report-overview-${date}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    toast({
      title: 'Success',
      description: 'Overview sheet exported to CSV',
    })
  }

  const exportDetailToCSV = () => {
    const headers = [
      'email',
      'name',
      'role',
      'className',
      'sectionName',
      'active',
      'lastLoginAt',
      'loginStatus',
      'createdAt',
    ]
    const csvRows = [headers.join(',')]

    for (const u of sortedUsers) {
      const lastLogin = u.lastLoginAt
        ? new Date(u.lastLoginAt).toISOString()
        : ''
      const loginStatus = u.lastLoginAt != null ? 'Logged in' : 'Never logged in'
      const createdAt = u.createdAt ? new Date(u.createdAt).toISOString() : ''
      const escape = (s: string) => `"${(s || '').replace(/"/g, '""')}"`
      const row = [
        escape(u.email),
        escape(u.name),
        u.role || '',
        escape(u.className ?? noClassLabel),
        escape(u.sectionName ?? noSectionLabel),
        u.active ? 'true' : 'false',
        lastLogin,
        loginStatus,
        createdAt,
      ].join(',')
      csvRows.push(row)
    }

    const csvContent = csvRows.join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `users-login-report-detail-${new Date().toISOString().split('T')[0]}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    toast({
      title: 'Success',
      description: 'Detail report exported to CSV',
    })
  }

  const formatDate = (s: string | null | undefined) => {
    if (!s) return 'Never'
    return new Date(s).toLocaleString()
  }

  if (loading && users.length === 0) {
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
      <div className="mb-6 flex items-center justify-between gap-4 flex-wrap">
        <div className="flex items-center gap-4">
          <Button variant="ghost" onClick={() => router.push('/analytics')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back
          </Button>
          <div>
            <h1 className="text-3xl font-bold">Users Report</h1>
            <p className="text-gray-600 mt-2">
              Logged-in vs never-logged-in users, grouped by class and section. Download overview (summary + by class/section) or detail (full list).
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          <Button onClick={exportOverviewToCSV} variant="outline" disabled={users.length === 0}>
            <Download className="h-4 w-4 mr-2" />
            Download overview sheet
          </Button>
          <Button onClick={exportDetailToCSV} variant="outline" disabled={users.length === 0}>
            <Download className="h-4 w-4 mr-2" />
            Download detail sheet
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total users</CardTitle>
            <Users className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{users.length}</div>
            <p className="text-xs text-muted-foreground">In current tenant</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total students</CardTitle>
            <Users className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{students.length}</div>
            <p className="text-xs text-muted-foreground">Role = STUDENT</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Logged in</CardTitle>
            <LogIn className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{loggedInCount}</div>
            <p className="text-xs text-muted-foreground">All users · Students: {studentsLoggedIn}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Never logged in</CardTitle>
            <UserX className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{neverLoggedInCount}</div>
            <p className="text-xs text-muted-foreground">All users · Students: {studentsNeverLoggedIn}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-row items-center justify-between flex-wrap gap-4">
            <div>
              <CardTitle>Users</CardTitle>
              <p className="text-sm text-muted-foreground mt-1">
                Sorted by class, then section. Class and section are from student enrollment (when available).
              </p>
            </div>
            <Select value={roleFilter} onValueChange={setRoleFilter}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="Filter by role" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All roles</SelectItem>
                <SelectItem value="STUDENT">STUDENT</SelectItem>
                <SelectItem value="INSTRUCTOR">INSTRUCTOR</SelectItem>
                <SelectItem value="TENANT_ADMIN">TENANT_ADMIN</SelectItem>
                <SelectItem value="CONTENT_MANAGER">CONTENT_MANAGER</SelectItem>
                <SelectItem value="SUPPORT_STAFF">SUPPORT_STAFF</SelectItem>
                <SelectItem value="SYSTEM_ADMIN">SYSTEM_ADMIN</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardHeader>
        <CardContent>
          {users.length === 0 ? (
            <p className="text-muted-foreground py-4">No users to display.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Email</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Class</TableHead>
                  <TableHead>Section</TableHead>
                  <TableHead>Last login</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sortedUsers.map((u) => (
                  <TableRow key={u.id}>
                    <TableCell className="font-medium">{u.email}</TableCell>
                    <TableCell>{u.name ?? '—'}</TableCell>
                    <TableCell>{u.role}</TableCell>
                    <TableCell>{u.className ?? noClassLabel}</TableCell>
                    <TableCell>{u.sectionName ?? noSectionLabel}</TableCell>
                    <TableCell>{formatDate(u.lastLoginAt)}</TableCell>
                    <TableCell>
                      {u.lastLoginAt != null ? (
                        <Badge variant="secondary">Logged in</Badge>
                      ) : (
                        <Badge variant="outline">Never logged in</Badge>
                      )}
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
