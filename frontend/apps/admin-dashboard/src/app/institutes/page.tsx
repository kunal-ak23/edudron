'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Plus, Loader2, Edit, Building2, Users } from 'lucide-react'
import { institutesApi } from '@/lib/api'
import type { Institute, CreateInstituteRequest } from '@kunal-ak23/edudron-shared-utils'
import { InstituteType } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export default function InstitutesPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreateDialog, setShowCreateDialog] = useState(false)
  const [formData, setFormData] = useState<CreateInstituteRequest>({
    name: '',
    code: '',
    type: InstituteType.SCHOOL,
    address: '',
    isActive: true
  })
  const [submitting, setSubmitting] = useState(false)

  const loadInstitutes = useCallback(async () => {
    try {
      setLoading(true)
      const allInstitutes = await institutesApi.listInstitutes()
      setInstitutes(allInstitutes || [])
    } catch (err: any) {
      console.error('Error loading institutes:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load institutes',
        description: errorMessage,
      })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    loadInstitutes()
  }, [loadInstitutes])

  const handleCreateInstitute = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)

    try {
      const newInstitute = await institutesApi.createInstitute(formData)
      toast({
        title: 'Institute created',
        description: `${newInstitute.name} has been created successfully.`,
      })
      setShowCreateDialog(false)
      setFormData({
        name: '',
        code: '',
        type: InstituteType.SCHOOL,
        address: '',
        isActive: true
      })
      loadInstitutes()
    } catch (err: any) {
      console.error('Error creating institute:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to create institute',
        description: errorMessage,
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleEdit = (institute: Institute) => {
    router.push(`/institutes/${institute.id}`)
  }

  const handleViewClasses = (institute: Institute) => {
    router.push(`/institutes/${institute.id}/classes`)
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  return (
    <div>
        <div className="flex justify-between items-center mb-8">
          <div>
            <Button onClick={() => setShowCreateDialog(true)}>
              <Plus className="h-4 w-4 mr-2" />
              Create New Institute
            </Button>
          </div>

          <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
            <DialogContent className="sm:max-w-[500px]">
              <form onSubmit={handleCreateInstitute}>
                <DialogHeader>
                  <DialogTitle>Create New Institute</DialogTitle>
                  <DialogDescription>
                    Add a new institute to the system. All fields are required.
                  </DialogDescription>
                </DialogHeader>
                <div className="grid gap-4 py-4">
                  <div className="grid gap-2">
                    <Label htmlFor="name">Institute Name</Label>
                    <Input
                      id="name"
                      value={formData.name}
                      onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      placeholder="e.g., ABC High School"
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="code">Institute Code</Label>
                    <Input
                      id="code"
                      value={formData.code}
                      onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                      placeholder="e.g., ABC001"
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="type">Institute Type</Label>
                    <Select
                      value={formData.type}
                      onValueChange={(value) => setFormData({ ...formData, type: value as InstituteType })}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Select type" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={InstituteType.SCHOOL}>School</SelectItem>
                        <SelectItem value={InstituteType.COLLEGE}>College</SelectItem>
                        <SelectItem value={InstituteType.UNIVERSITY}>University</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="address">Address (Optional)</Label>
                    <Input
                      id="address"
                      value={formData.address || ''}
                      onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                      placeholder="Institute address"
                    />
                  </div>
                </div>
                <DialogFooter>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setShowCreateDialog(false)}
                    disabled={submitting}
                  >
                    Cancel
                  </Button>
                  <Button type="submit" disabled={submitting}>
                    {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Create Institute
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        </div>

        <Card>
            <CardHeader>
              <CardTitle>All Institutes</CardTitle>
            </CardHeader>
            <CardContent>
              {institutes.length === 0 ? (
                <div className="text-center py-12">
                  <Building2 className="mx-auto h-12 w-12 text-gray-400" />
                  <h3 className="mt-2 text-sm font-semibold text-gray-900">No institutes</h3>
                  <p className="mt-1 text-sm text-gray-500">Get started by creating a new institute.</p>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Code</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Classes</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {institutes.map((institute) => (
                      <TableRow key={institute.id}>
                        <TableCell className="font-medium">{institute.name}</TableCell>
                        <TableCell>{institute.code}</TableCell>
                        <TableCell>
                          <Badge variant="outline">{institute.type}</Badge>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1">
                            <Users className="h-4 w-4 text-gray-400" />
                            {institute.classCount || 0}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={institute.isActive ? 'default' : 'secondary'}>
                            {institute.isActive ? 'Active' : 'Inactive'}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleViewClasses(institute)}
                            >
                              View Classes
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleEdit(institute)}
                            >
                              <Edit className="h-4 w-4 mr-1" />
                              Edit
                            </Button>
                          </div>
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

