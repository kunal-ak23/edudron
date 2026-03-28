'use client'

import React, { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Loader2, ArrowLeft, Save } from 'lucide-react'
import { Switch } from '@/components/ui/switch'
import { enrollmentsApi } from '@/lib/api'
import type { Batch } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'

export default function BatchDetailPage() {
  const router = useRouter()
  const params = useParams()
  const batchId = params.id as string
  const { toast } = useToast()
  const [batch, setBatch] = useState<Batch | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)

  // Edit form state
  const [formData, setFormData] = useState({
    name: '',
    startDate: '',
    endDate: '',
    maxStudents: 0,
    isActive: true,
  })

  const loadBatch = useCallback(async () => {
    try {
      setLoading(true)
      const batchData = await enrollmentsApi.getBatch(batchId)
      setBatch(batchData)
      setFormData({
        name: batchData.name,
        startDate: batchData.startDate?.split('T')[0] || '',
        endDate: batchData.endDate?.split('T')[0] || '',
        maxStudents: batchData.maxStudents || batchData.capacity || 0,
        isActive: batchData.isActive,
      })
    } catch (err: any) {
      toast({
        variant: 'destructive',
        title: 'Failed to load batch',
        description: extractErrorMessage(err),
      })
      router.push('/batches')
    } finally {
      setLoading(false)
    }
  }, [batchId, toast, router])

  useEffect(() => {
    if (batchId) {
      loadBatch()
    }
  }, [batchId, loadBatch])

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)

    try {
      const updated = await enrollmentsApi.updateBatch(batchId, formData)
      setBatch(updated)
      toast({
        title: 'Batch updated',
        description: `${updated.name} has been updated successfully.`,
      })
    } catch (err: any) {
      toast({
        variant: 'destructive',
        title: 'Failed to update batch',
        description: extractErrorMessage(err),
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async () => {
    try {
      await enrollmentsApi.deleteBatch(batchId)
      toast({
        title: 'Batch deactivated',
        description: 'The batch has been deactivated successfully.',
      })
      router.push('/batches')
    } catch (err: any) {
      toast({
        variant: 'destructive',
        title: 'Failed to deactivate batch',
        description: extractErrorMessage(err),
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

  if (!batch) {
    return null
  }

  return (
    <>
      <div>
        <div className="mb-4 flex items-center gap-2 text-sm text-gray-600">
          <button onClick={() => router.push('/batches')} className="hover:text-gray-900">Batches</button>
          <span>/</span>
          <span className="text-gray-900">{batch.name}</span>
        </div>

        <div className="flex items-center justify-between mb-4">
          <Button variant="ghost" onClick={() => router.push('/batches')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Batches
          </Button>
        </div>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Batch Details</CardTitle>
              <Badge variant={batch.isActive ? 'default' : 'secondary'}>
                {batch.isActive ? 'Active' : 'Inactive'}
              </Badge>
            </div>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleUpdate} className="space-y-6">
              <div className="grid gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="name">Batch Name</Label>
                  <Input
                    id="name"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    required
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="startDate">Start Date</Label>
                    <Input
                      id="startDate"
                      type="date"
                      value={formData.startDate}
                      onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="endDate">End Date</Label>
                    <Input
                      id="endDate"
                      type="date"
                      value={formData.endDate}
                      onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                    />
                  </div>
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="maxStudents">Max Students</Label>
                  <Input
                    id="maxStudents"
                    type="number"
                    value={formData.maxStudents}
                    onChange={(e) => setFormData({ ...formData, maxStudents: parseInt(e.target.value) || 0 })}
                  />
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <Label>Active</Label>
                    <p className="text-sm text-muted-foreground">
                      Whether this batch is currently active
                    </p>
                  </div>
                  <Switch
                    checked={formData.isActive}
                    onCheckedChange={(checked: boolean) => setFormData({ ...formData, isActive: checked })}
                  />
                </div>
              </div>
              <div className="flex justify-between">
                <Button
                  type="button"
                  variant="destructive"
                  onClick={() => setShowDeleteDialog(true)}
                >
                  Deactivate Batch
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

      </div>

      <ConfirmationDialog
        isOpen={showDeleteDialog}
        onClose={() => setShowDeleteDialog(false)}
        onConfirm={handleDelete}
        title="Deactivate Batch"
        description="Are you sure you want to deactivate this batch?"
        confirmText="Deactivate"
        variant="destructive"
      />
    </>
  )
}
