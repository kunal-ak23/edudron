'use client'

import { useState, useEffect, useCallback } from 'react'
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Loader2, Plus, Search, Mail, Phone, User, ChevronLeft, ChevronRight } from 'lucide-react'
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
  const [showAddDialog, setShowAddDialog] = useState(false)
  const [creating, setCreating] = useState(false)
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  
  // Form state for adding student
  const [newStudent, setNewStudent] = useState({
    name: '',
    email: '',
    phone: '',
    password: '',
    autoGeneratePassword: true,
  })

  const loadStudents = useCallback(async () => {
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
  }, [toast])

  useEffect(() => {
    loadStudents()
  }, [loadStudents])

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

  const filteredStudents = students.filter(student =>
    student.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    student.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (student.phone && student.phone.includes(searchTerm))
  )

  // Pagination calculations
  const totalElements = filteredStudents.length
  const totalPages = Math.ceil(totalElements / pageSize)
  const startIndex = currentPage * pageSize
  const endIndex = startIndex + pageSize
  const paginatedStudents = filteredStudents.slice(startIndex, endIndex)

  // Reset to first page when search term changes
  useEffect(() => {
    setCurrentPage(0)
  }, [searchTerm])

  const handleAddStudent = async () => {
    if (!newStudent.name || !newStudent.email) {
      toast({
        variant: 'destructive',
        title: 'Validation error',
        description: 'Name and email are required',
      })
      return
    }

    if (!newStudent.autoGeneratePassword && !newStudent.password) {
      toast({
        variant: 'destructive',
        title: 'Validation error',
        description: 'Password is required when auto-generate password is disabled',
      })
      return
    }

    setCreating(true)
    try {
      const requestBody: any = {
        name: newStudent.name,
        email: newStudent.email,
        role: 'STUDENT',
        active: true,
        autoGeneratePassword: newStudent.autoGeneratePassword,
      }

      if (newStudent.phone) {
        requestBody.phone = newStudent.phone
      }

      if (!newStudent.autoGeneratePassword && newStudent.password) {
        requestBody.password = newStudent.password
      }

      await apiClient.post('/idp/users', requestBody)
      
      toast({
        title: 'Student created',
        description: 'Student has been successfully created',
      })

      // Reset form
      setNewStudent({
        name: '',
        email: '',
        phone: '',
        password: '',
        autoGeneratePassword: true,
      })
      setShowAddDialog(false)

      // Reload students
      await loadStudents()
    } catch (err: any) {
      console.error('Error creating student:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to create student',
        description: errorMessage,
      })
    } finally {
      setCreating(false)
    }
  }

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
          <div className="flex gap-2">
            <Button onClick={() => setShowAddDialog(true)}>
              <Plus className="h-4 w-4 mr-2" />
              Add Student
            </Button>
            <Link href="/students/import">
              <Button variant="outline">
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
                    {paginatedStudents.map((student) => (
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
              
              {/* Pagination Controls */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between mt-4 pt-4 border-t">
                  <div className="flex items-center gap-2">
                    <Label className="text-sm">Page size:</Label>
                    <Select
                      value={pageSize.toString()}
                      onValueChange={(value) => {
                        setPageSize(Number(value))
                        setCurrentPage(0) // Reset to first page when changing page size
                      }}
                    >
                      <SelectTrigger className="w-20">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="10">10</SelectItem>
                        <SelectItem value="20">20</SelectItem>
                        <SelectItem value="50">50</SelectItem>
                        <SelectItem value="100">100</SelectItem>
                      </SelectContent>
                    </Select>
                    <span className="text-sm text-gray-600">
                      Showing {startIndex + 1} to {Math.min(endIndex, totalElements)} of {totalElements.toLocaleString()}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage(0)}
                      disabled={currentPage === 0 || loading}
                    >
                      First
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
                      disabled={currentPage === 0 || loading}
                    >
                      <ChevronLeft className="h-4 w-4" />
                      Previous
                    </Button>
                    <span className="text-sm text-gray-600 px-2">
                      Page {currentPage + 1} of {totalPages}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
                      disabled={currentPage >= totalPages - 1 || loading}
                    >
                      Next
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage(totalPages - 1)}
                      disabled={currentPage >= totalPages - 1 || loading}
                    >
                      Last
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

      {/* Add Student Dialog */}
      <Dialog open={showAddDialog} onOpenChange={setShowAddDialog}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Add New Student</DialogTitle>
            <DialogDescription>
              Create a new student account. You can auto-generate a password or set one manually.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="name">Name *</Label>
              <Input
                id="name"
                placeholder="Enter student name"
                value={newStudent.name}
                onChange={(e) => setNewStudent({ ...newStudent, name: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">Email *</Label>
              <Input
                id="email"
                type="email"
                placeholder="Enter student email"
                value={newStudent.email}
                onChange={(e) => setNewStudent({ ...newStudent, email: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">Phone (Optional)</Label>
              <Input
                id="phone"
                type="tel"
                placeholder="Enter student phone"
                value={newStudent.phone}
                onChange={(e) => setNewStudent({ ...newStudent, phone: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="autoGeneratePassword"
                  checked={newStudent.autoGeneratePassword}
                  onChange={(e) => setNewStudent({ ...newStudent, autoGeneratePassword: e.target.checked, password: '' })}
                  className="rounded"
                />
                <Label htmlFor="autoGeneratePassword" className="cursor-pointer">
                  Auto-generate password
                </Label>
              </div>
            </div>
            {!newStudent.autoGeneratePassword && (
              <div className="space-y-2">
                <Label htmlFor="password">Password *</Label>
                <Input
                  id="password"
                  type="password"
                  placeholder="Enter password"
                  value={newStudent.password}
                  onChange={(e) => setNewStudent({ ...newStudent, password: e.target.value })}
                />
              </div>
            )}
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowAddDialog(false)
                setNewStudent({
                  name: '',
                  email: '',
                  phone: '',
                  password: '',
                  autoGeneratePassword: true,
                })
              }}
              disabled={creating}
            >
              Cancel
            </Button>
            <Button
              onClick={handleAddStudent}
              disabled={creating || !newStudent.name || !newStudent.email}
            >
              {creating ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Creating...
                </>
              ) : (
                'Create Student'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      </div>
  )
}

