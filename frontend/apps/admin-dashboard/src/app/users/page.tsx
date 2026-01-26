'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
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
import { Plus, Loader2, ChevronLeft, ChevronRight } from 'lucide-react'
import { apiClient } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

interface User {
  id: string
  name: string
  email: string
  role: string
  active: boolean
  instituteIds?: string[]
  createdAt: string
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

export default function UsersPage() {
  const router = useRouter()
  const { toast } = useToast()
  const { user } = useAuth()
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState<string>('all')
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('')
  const searchInputRef = useRef<HTMLInputElement>(null)
  
  const isSystemAdmin = user?.role === 'SYSTEM_ADMIN'
  const isTenantAdmin = user?.role === 'TENANT_ADMIN'
  const isContentManager = user?.role === 'CONTENT_MANAGER'
  const canManageUsers = isSystemAdmin || isTenantAdmin

  const loadUsers = useCallback(async () => {
    try {
      setLoading(true)

      // Build query parameters with filters
      const params = new URLSearchParams()
      params.append('page', currentPage.toString())
      params.append('size', pageSize.toString())
      
      // Add role filter if not 'all'
      if (roleFilter && roleFilter !== 'all') {
        params.append('role', roleFilter)
      }
      
      // Add search filter if provided (use debounced value)
      const searchToUse = debouncedSearchTerm || searchQuery
      if (searchToUse.trim()) {
        params.append('search', searchToUse.trim())
      }

      console.log('[UsersPage] Loading users with filters:', { 
        page: currentPage, 
        size: pageSize,
        role: roleFilter !== 'all' ? roleFilter : null,
        search: searchToUse.trim() || null 
      })

      // Use paginated endpoint with backend filtering
      try {
        const response = await apiClient.get<PaginatedResponse<User>>(
          `/idp/users/paginated?${params.toString()}`
        )
        const paginatedData = response.data || { content: [], totalElements: 0, totalPages: 0, number: 0, size: pageSize, first: true, last: true }
        
        console.log('[UsersPage] Received response:', {
          contentLength: paginatedData.content?.length,
          totalElements: paginatedData.totalElements,
          totalPages: paginatedData.totalPages
        })
        
        setUsers(paginatedData.content || [])
        setTotalElements(paginatedData.totalElements || 0)
        setTotalPages(paginatedData.totalPages || 0)
      } catch (paginatedError: any) {
        // Fallback to non-paginated endpoint if paginated endpoint doesn't exist
        console.warn('Paginated endpoint failed, falling back to non-paginated:', paginatedError)
        const response = await apiClient.get<User[]>('/idp/users')
        
        let usersData: User[] = []
        if (Array.isArray(response)) {
          usersData = response
        } else if (response && 'data' in response && Array.isArray(response.data)) {
          usersData = response.data as User[]
        }
        
        // Apply client-side filtering if we had to fall back
        if (searchToUse.trim()) {
          const query = searchToUse.toLowerCase()
          usersData = usersData.filter(
            (user) =>
              user.name.toLowerCase().includes(query) ||
              user.email.toLowerCase().includes(query) ||
              user.role.toLowerCase().includes(query)
          )
        }
        
        setUsers(usersData)
        setTotalElements(usersData.length)
        setTotalPages(Math.ceil(usersData.length / pageSize))
      }
    } catch (err: any) {
      console.error('Error loading users:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load users',
        description: errorMessage,
      })
      setUsers([])
      setTotalElements(0)
      setTotalPages(0)
    } finally {
      setLoading(false)
    }
  }, [toast, currentPage, pageSize, roleFilter, debouncedSearchTerm])

  // Debounce search term to avoid too many API calls
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setDebouncedSearchTerm(searchQuery)
    }, 300) // 300ms debounce
    
    return () => clearTimeout(timeoutId)
  }, [searchQuery])

  // Reset to first page when search term, role filter, or page size changes
  useEffect(() => {
    setCurrentPage(0)
  }, [searchQuery, roleFilter, pageSize])

  useEffect(() => {
    loadUsers()
  }, [currentPage, pageSize, roleFilter, debouncedSearchTerm, loadUsers])

  const getRoleBadgeVariant = (role: string): "default" | "secondary" | "destructive" | "outline" => {
    switch (role) {
      case 'SYSTEM_ADMIN':
        return 'destructive'
      case 'TENANT_ADMIN':
        return 'default'
      case 'CONTENT_MANAGER':
        return 'default'
      case 'INSTRUCTOR':
        return 'secondary'
      case 'STUDENT':
        return 'outline'
      default:
        return 'outline'
    }
  }

  // CONTENT_MANAGER can view users (read-only), others who can't manage are redirected
  useEffect(() => {
    if (user && !canManageUsers && !isContentManager) {
      router.push('/unauthorized')
    }
  }, [user, canManageUsers, isContentManager, router])
  
  return (
    <div>
        <div className="mb-6 flex items-center justify-between">
          <div>
            {canManageUsers && (
              <Button onClick={() => router.push('/users/new')}>
                <Plus className="h-4 w-4 mr-2" />
                Add User
              </Button>
            )}
            {isContentManager && (
              <p className="text-sm text-muted-foreground">
                View-only access: You can view users but cannot create or edit them
              </p>
            )}
          </div>
        </div>

        {/* Search and Filters */}
        <div className="mb-6 flex gap-4">
          <Input
            ref={searchInputRef}
            placeholder="Search users by name, email, or role..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="max-w-sm"
          />
          <Select
            value={roleFilter}
            onValueChange={setRoleFilter}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder="Filter by role" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Roles</SelectItem>
              <SelectItem value="SYSTEM_ADMIN">System Admin</SelectItem>
              <SelectItem value="TENANT_ADMIN">Tenant Admin</SelectItem>
              <SelectItem value="CONTENT_MANAGER">Content Manager</SelectItem>
              <SelectItem value="INSTRUCTOR">Instructor</SelectItem>
              <SelectItem value="SUPPORT_STAFF">Support Staff</SelectItem>
              <SelectItem value="STUDENT">Student</SelectItem>
            </SelectContent>
          </Select>
        </div>

          {/* Users List */}
          {loading ? (
            <Card>
              <CardContent className="py-12">
                <div className="text-center">
                  <Loader2 className="h-8 w-8 animate-spin text-primary mx-auto" />
                </div>
              </CardContent>
            </Card>
          ) : users.length === 0 ? (
            <Card>
              <CardContent className="text-center py-12">
                <p className="text-muted-foreground mb-4">
                  {searchQuery ? 'No users found matching your search' : 'No users found'}
                </p>
                {!searchQuery && canManageUsers && (
                  <Button onClick={() => router.push('/users/new')}>
                    <Plus className="h-4 w-4 mr-2" />
                    Add Your First User
                  </Button>
                )}
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>User</TableHead>
                      <TableHead>Role</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Created</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {users.map((user) => (
                      <TableRow key={user.id}>
                        <TableCell>
                          <div className="flex items-center">
                            <div className="w-10 h-10 bg-primary rounded-full flex items-center justify-center text-primary-foreground font-semibold">
                              {user.name.charAt(0).toUpperCase()}
                            </div>
                            <div className="ml-4">
                              <div className="font-medium">{user.name}</div>
                              <div className="text-sm text-muted-foreground">{user.email}</div>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={getRoleBadgeVariant(user.role)}>
                            {user.role.replace('_', ' ')}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={user.active ? 'default' : 'secondary'}>
                            {user.active ? 'Active' : 'Inactive'}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {new Date(user.createdAt).toLocaleDateString()}
                        </TableCell>
                        <TableCell className="text-right">
                          {canManageUsers && (
                            <Button 
                              variant="ghost" 
                              size="sm"
                              onClick={() => router.push(`/users/${user.id}/edit`)}
                              disabled={isTenantAdmin && ['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER'].includes(user.role)}
                            >
                              Edit
                            </Button>
                          )}
                          {isContentManager && (
                            <span className="text-sm text-muted-foreground">View only</span>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                
                {/* Pagination Controls */}
                {totalPages > 1 && (
                  <div className="flex items-center justify-between mt-4 pt-4 border-t px-6 pb-4">
                    <div className="flex items-center gap-2">
                      <Label className="text-sm">Page size:</Label>
                      <Select
                        value={pageSize.toString()}
                        onValueChange={(value) => {
                          setPageSize(Number(value))
                          setCurrentPage(0)
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
                        Showing {currentPage * pageSize + 1} to {Math.min((currentPage + 1) * pageSize, totalElements)} of {totalElements.toLocaleString()}
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
          )}
      </div>
  )
}

