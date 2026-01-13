'use client'

import { useState, useEffect } from 'react'
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
import { Loader2, Plus, Search, Mail, Phone, User } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import { apiClient } from '@/lib/api'
import Link from 'next/link'

interface Student {
  id: string
  name: string
  email: string
  phone?: string
  role: string
  active: boolean
  createdAt: string
  lastLoginAt?: string
}

export default function StudentsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const { user, isAuthenticated } = useAuth()
  const [students, setStudents] = useState<Student[]>([])
  const [loading, setLoading] = useState(true)
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    loadStudents()
  }, [])

  // Role-based access control
  useEffect(() => {
    if (!isAuthenticated() || !user) {
      router.push('/login')
      return
    }
    
    const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN']
    if (!allowedRoles.includes(user.role)) {
      router.push('/unauthorized')
    }
  }, [user, isAuthenticated, router])

  const loadStudents = async () => {
    try {
      setLoading(true)

      // Use role-specific endpoint via ApiClient (handles auth automatically)
      try {
        const response = await apiClient.get<Student[]>('/idp/users/role/STUDENT')
        // ApiClient.get always returns ApiResponse<T> with a data property
        const students = response.data || []
        setStudents(students)
      } catch (roleError) {
        // Fallback to filtering all users if role endpoint doesn't work
        console.warn('Role-specific endpoint failed, falling back to all users:', roleError)
        try {
          const allUsersResponse = await apiClient.get<Student[]>('/idp/users')
          const allUsers = allUsersResponse.data || []
          const studentUsers = allUsers.filter((user: Student) => user.role === 'STUDENT')
          setStudents(studentUsers)
        } catch (fallbackError) {
          throw new Error('Failed to load students from both endpoints')
        }
      }
    } catch (err: any) {
      console.error('Error loading students:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load students',
        description: errorMessage,
      })
    } finally {
      setLoading(false)
    }
  }

  const filteredStudents = students.filter(student =>
    student.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    student.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (student.phone && student.phone.includes(searchTerm))
  )

  if (!user || !isAuthenticated()) {
    return null
  }

  const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN']
  if (!allowedRoles.includes(user.role)) {
    return null
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div>
        <div className="mb-6 flex items-center justify-between">
          <div>
            <Link href="/students/import">
              <Button>
                <Plus className="h-4 w-4 mr-2" />
                Import Students
              </Button>
            </Link>
          </div>
        </div>

        <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>All Students ({filteredStudents.length})</CardTitle>
                <div className="relative w-64">
                  <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Search students..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="pl-8"
                  />
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {filteredStudents.length === 0 ? (
                <div className="text-center py-12">
                  <User className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
                  <p className="text-muted-foreground">
                    {searchTerm ? 'No students found matching your search.' : 'No students found.'}
                  </p>
                  {!searchTerm && (
                    <Link href="/students/import">
                      <Button variant="outline" className="mt-4">
                        <Plus className="h-4 w-4 mr-2" />
                        Import Students
                      </Button>
                    </Link>
                  )}
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Email</TableHead>
                      <TableHead>Phone</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Created</TableHead>
                      <TableHead>Last Login</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredStudents.map((student) => (
                      <TableRow key={student.id}>
                        <TableCell className="font-medium">{student.name}</TableCell>
                        <TableCell>
                          <div className="flex items-center">
                            <Mail className="h-4 w-4 mr-2 text-muted-foreground" />
                            {student.email}
                          </div>
                        </TableCell>
                        <TableCell>
                          {student.phone ? (
                            <div className="flex items-center">
                              <Phone className="h-4 w-4 mr-2 text-muted-foreground" />
                              {student.phone}
                            </div>
                          ) : (
                            <span className="text-muted-foreground">—</span>
                          )}
                        </TableCell>
                        <TableCell>
                          <Badge variant={student.active ? 'default' : 'secondary'}>
                            {student.active ? 'Active' : 'Inactive'}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {student.createdAt
                            ? new Date(student.createdAt).toLocaleDateString()
                            : '—'}
                        </TableCell>
                        <TableCell>
                          {student.lastLoginAt
                            ? new Date(student.lastLoginAt).toLocaleDateString()
                            : 'Never'}
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

