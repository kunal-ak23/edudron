'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, ArrowLeft, Save } from 'lucide-react'
import { institutesApi, classesApi } from '@/lib/api'
import type { Institute, CreateClassRequest } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'

export default function CreateClassPage() {
  const router = useRouter()
  const params = useParams()
  const instituteId = params.id as string
  const { toast } = useToast()
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [formData, setFormData] = useState<CreateClassRequest>({
    name: '',
    code: '',
    instituteId: instituteId,
    academicYear: '',
    grade: '',
    level: '',
    isActive: true
  })

  const loadInstitute = useCallback(async () => {
    try {
      setLoading(true)
      const data = await institutesApi.getInstitute(instituteId)
      setInstitute(data)
    } catch (err: any) {
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
  }, [instituteId, toast, router])

  useEffect(() => {
    if (instituteId) {
      loadInstitute()
      setFormData(prev => ({ ...prev, instituteId: instituteId }))
    }
  }, [instituteId, loadInstitute])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)

    try {
      const newClass = await classesApi.createClass(instituteId, formData)
      toast({
        title: 'Class created',
        description: `${newClass.name} has been created successfully.`,
      })
      router.push(`/institutes/${instituteId}/classes`)
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to create class',
        description: errorMessage,
      })
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      
        <div className="min-h-screen flex items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
  )
}

  if (!institute) {
    return null
  }

  return (
    
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <Link href={`/institutes/${instituteId}/classes`}>
            <Button variant="ghost" className="mb-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Classes
            </Button>
          </Link>

          <div className="mb-6">
            <h1 className="text-3xl font-bold text-gray-900">Create New Class</h1>
            <p className="mt-2 text-sm text-gray-600">Add a new class to {institute.name}</p>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Class Details</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-6">
                <div className="grid gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="name">Class Name</Label>
                    <Input
                      id="name"
                      value={formData.name}
                      onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      placeholder="e.g., Class 10A"
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="code">Class Code</Label>
                    <Input
                      id="code"
                      value={formData.code}
                      onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                      placeholder="e.g., 10A"
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="academicYear">Academic Year (Optional)</Label>
                    <Input
                      id="academicYear"
                      value={formData.academicYear || ''}
                      onChange={(e) => setFormData({ ...formData, academicYear: e.target.value })}
                      placeholder="e.g., 2024-25"
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="grade">Grade (Optional)</Label>
                    <Input
                      id="grade"
                      value={formData.grade || ''}
                      onChange={(e) => setFormData({ ...formData, grade: e.target.value })}
                      placeholder="e.g., 10, MBA"
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="level">Level (Optional)</Label>
                    <Input
                      id="level"
                      value={formData.level || ''}
                      onChange={(e) => setFormData({ ...formData, level: e.target.value })}
                      placeholder="e.g., Undergraduate, Graduate"
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
                <div className="flex justify-end">
                  <Button type="submit" disabled={submitting}>
                    {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    <Save className="mr-2 h-4 w-4" />
                    Create Class
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
  )
}


