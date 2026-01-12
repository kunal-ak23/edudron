'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute } from '@edudron/ui-components'
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
import { Plus, Loader2 } from 'lucide-react'
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
  createdAt: string
}

export default function UsersPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [users, setUsers] = useState<User[]>([])
  const [filteredUsers, setFilteredUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')

  useEffect(() => {
    loadUsers()
  }, [])

  useEffect(() => {
    filterUsers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [users, searchQuery])

  const loadUsers = async () => {
    try {
      // Spring Boot returns ResponseEntity<List<UserDTO>> which serializes as an array
      // ApiClient.get returns response.data, which will be the array directly
      const response = await apiClient.get<User[]>('/idp/users')
      console.log('Users response:', response) // Debug log
      
      // The response might be the array directly, or wrapped in {data: [...]}
      let usersData: User[] = []
      if (Array.isArray(response)) {
        usersData = response
      } else if (response && 'data' in response && Array.isArray(response.data)) {
        usersData = response.data as User[]
      }
      
      console.log('Parsed users:', usersData) // Debug log
      setUsers(usersData)
      setFilteredUsers(usersData)
    } catch (error: any) {
      console.error('Failed to load users:', error)
      const errorMessage = extractErrorMessage(error)
      toast({
        variant: 'destructive',
        title: 'Failed to load users',
        description: errorMessage,
      })
      setUsers([])
      setFilteredUsers([])
    } finally {
      setLoading(false)
    }
  }

  const filterUsers = () => {
    if (!searchQuery) {
      setFilteredUsers(users)
      return
    }

    const query = searchQuery.toLowerCase()
    const filtered = users.filter(
      (user) =>
        user.name.toLowerCase().includes(query) ||
        user.email.toLowerCase().includes(query) ||
        user.role.toLowerCase().includes(query)
    )
    setFilteredUsers(filtered)
  }

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

  return (
    <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN']}>
      <div>
        <div className="mb-6 flex items-center justify-between">
          <div>
            <Button onClick={() => router.push('/users/new')}>
              <Plus className="h-4 w-4 mr-2" />
              Add User
            </Button>
          </div>
        </div>

        {/* Search */}
        <div className="mb-6">
            <Input
              placeholder="Search users by name, email, or role..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="max-w-sm"
            />
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
          ) : filteredUsers.length === 0 ? (
            <Card>
              <CardContent className="text-center py-12">
                <p className="text-muted-foreground mb-4">
                  {searchQuery ? 'No users found matching your search' : 'No users found'}
                </p>
                {!searchQuery && (
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
                    {filteredUsers.map((user) => (
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
                          <Button variant="ghost" size="sm">
                            Edit
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}
      </div>
    </ProtectedRoute>
  )
}

