'use client'

import React, { useState, useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Loader2, ArrowLeft, Save, Network } from 'lucide-react'
import { institutesApi } from '@/lib/api'
import type { Institute, CreateInstituteRequest } from '@kunal-ak23/edudron-shared-utils'
import { InstituteType } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'

export default function InstituteDetailPage() {
  const router = useRouter()
  const params = useParams()
  const instituteId = params.id as string
  const { toast } = useToast()
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [formData, setFormData] = useState<CreateInstituteRequest>({
    name: '',
    code: '',
    type: InstituteType.SCHOOL,
    address: '',
    isActive: true
  })

  useEffect(() => {
    if (instituteId) {
      loadInstitute()
    }
  }, [instituteId])

  const loadInstitute = async () => {
    try {
      setLoading(true)
      const data = await institutesApi.getInstitute(instituteId)
      setInstitute(data)
      setFormData({
        name: data.name,
        code: data.code,
        type: data.type,
        address: data.address || '',
        isActive: data.isActive
      })
    } catch (err: any) {
      console.error('Error loading institute:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load institute',
        description: errorMessage,
      })
      router.push('/institutes')
    } finally {
      setLoading(false)
    }
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)

    try {
      const updated = await institutesApi.updateInstitute(instituteId, formData)
      setInstitute(updated)
      toast({
        title: 'Institute updated',
        description: `${updated.name} has been updated successfully.`,
      })
    } catch (err: any) {
      console.error('Error updating institute:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to update institute',
        description: errorMessage,
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async () => {
    try {
      await institutesApi.deleteInstitute(instituteId)
      toast({
        title: 'Institute deactivated',
        description: 'The institute has been deactivated successfully.',
      })
      router.push('/institutes')
    } catch (err: any) {
      console.error('Error deleting institute:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to deactivate institute',
        description: errorMessage,
      })
    } finally {
      setShowDeleteDialog(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!institute) {
    return null
  }

  return (
    <>
      <div>
        <Link href="/institutes">
          <Button variant="ghost" className="mb-6">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Institutes
          </Button>
        </Link>

        <Card>
          <CardHeader>
            <CardTitle>Institute Details</CardTitle>
          </CardHeader>
          <CardContent>
              <form onSubmit={handleUpdate} className="space-y-6">
                <div className="grid gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="name">Institute Name</Label>
                    <Input
                      id="name"
                      value={formData.name}
                      onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="code">Institute Code</Label>
                    <Input
                      id="code"
                      value={formData.code}
                      onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
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
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={InstituteType.SCHOOL}>School</SelectItem>
                        <SelectItem value={InstituteType.COLLEGE}>College</SelectItem>
                        <SelectItem value={InstituteType.UNIVERSITY}>University</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="address">Address</Label>
                    <Input
                      id="address"
                      value={formData.address || ''}
                      onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      id="isActive"
                      checked={formData.isActive}
                      onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                      className="rounded"
                    />
                    <Label htmlFor="isActive">Active</Label>
                  </div>
                </div>
                <div className="flex justify-between">
                  <Button
                    type="button"
                    variant="destructive"
                    onClick={() => setShowDeleteDialog(true)}
                  >
                    Deactivate Institute
                  </Button>
                  <Button type="submit" disabled={submitting}>
                    {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    <Save className="mr-2 h-4 w-4" />
                    Save Changes
                  </Button>
                </div>
              </form>
          </CardContent>
        </Card>

        <Card className="mt-6">
          <CardHeader>
            <CardTitle>Quick Actions</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <Link href={`/institutes/${instituteId}/classes`}>
              <Button variant="outline" className="w-full">
                View Classes ({institute.classCount || 0})
              </Button>
            </Link>
            <Link href={`/institutes/${instituteId}/tree`}>
              <Button variant="outline" className="w-full">
                <Network className="h-4 w-4 mr-2" />
                View Tree Visualization
              </Button>
            </Link>
          </CardContent>
        </Card>
      </div>

      <ConfirmationDialog
        isOpen={showDeleteDialog}
        onClose={() => setShowDeleteDialog(false)}
        onConfirm={handleDelete}
        title="Deactivate Institute"
        description="Are you sure you want to deactivate this institute?"
        confirmText="Deactivate"
        variant="destructive"
      />
    </>
  )
}


