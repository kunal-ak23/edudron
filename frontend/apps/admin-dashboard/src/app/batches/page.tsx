'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Card, Button } from '@kunal-ak23/edudron-ui-components'
import { enrollmentsApi, coursesApi } from '@/lib/api'
import type { Batch, Course } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

export default function BatchesPage() {
  const router = useRouter()
  const [batches, setBatches] = useState<Batch[]>([])
  const [courses, setCourses] = useState<Record<string, Course>>({})
  const [loading, setLoading] = useState(true)
  const { toast } = useToast()
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [batchToDelete, setBatchToDelete] = useState<string | null>(null)

  useEffect(() => {
    loadBatches()
  }, [])

  const loadBatches = async () => {
    try {
      const batchesData = await enrollmentsApi.listBatches()
      setBatches(batchesData)

      // Load course details
      const courseIds = Array.from(new Set(batchesData.map((b) => b.courseId)))
      const coursePromises = courseIds.map((id) =>
        coursesApi.getCourse(id).catch(() => null)
      )
      const coursesData = await Promise.all(coursePromises)
      const coursesMap: Record<string, Course> = {}
      coursesData.forEach((course, index) => {
        if (course) {
          coursesMap[courseIds[index]] = course
        }
      })
      setCourses(coursesMap)
    } catch (error) {
      console.error('Failed to load batches:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async () => {
    if (batchToDelete) {
      try {
        await enrollmentsApi.deleteBatch(batchToDelete)
        await loadBatches()
        toast({
          title: 'Batch deactivated',
          description: 'The batch has been deactivated successfully.',
        })
      } catch (error) {
        const errorMessage = extractErrorMessage(error)
        toast({
          variant: 'destructive',
          title: 'Failed to delete batch',
          description: errorMessage,
        })
      } finally {
        setShowDeleteDialog(false)
        setBatchToDelete(null)
      }
    }
  }

  return (
    <div>
        <div className="mb-6 flex items-center justify-between">
          <div>
            <Button onClick={() => router.push('/batches/new')}>
              <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Create Batch
            </Button>
          </div>
        </div>

        {/* Batch List */}
        {loading ? (
            <Card loading={true} />
          ) : batches.length === 0 ? (
            <Card>
              <div className="text-center py-12">
                <p className="text-gray-500 mb-4">No batches found</p>
                <Button onClick={() => router.push('/batches/new')}>
                  Create Your First Batch
                </Button>
              </div>
            </Card>
          ) : (
            <div className="space-y-4">
              {batches.map((batch) => {
                const course = courses[batch.courseId]
                return (
                  <Card key={batch.id}>
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-3 mb-2">
                          <h3 className="text-lg font-semibold text-gray-900">{batch.name}</h3>
                          <span
                            className={`px-2 py-1 rounded text-xs font-medium ${
                              batch.isActive
                                ? 'bg-green-100 text-green-700'
                                : 'bg-gray-100 text-gray-700'
                            }`}
                          >
                            {batch.isActive ? 'Active' : 'Inactive'}
                          </span>
                        </div>
                        {course && (
                          <p className="text-sm text-gray-600 mb-3">Course: {course.title}</p>
                        )}
                        <div className="flex items-center space-x-6 text-sm text-gray-500">
                          <span>
                            Start: {new Date(batch.startDate).toLocaleDateString()}
                          </span>
                          <span>
                            End: {new Date(batch.endDate).toLocaleDateString()}
                          </span>
                          {(batch.capacity || batch.maxStudents) && (
                            <span>
                              Capacity: {batch.enrolledCount ?? batch.studentCount ?? 0} / {batch.capacity ?? batch.maxStudents}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center space-x-2 ml-4">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => router.push(`/batches/${batch.id}`)}
                        >
                          View Details
                        </Button>
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={() => {
                            setBatchToDelete(batch.id)
                            setShowDeleteDialog(true)
                          }}
                        >
                          Deactivate
                        </Button>
                      </div>
                    </div>
                  </Card>
                )
              })}
            </div>
          )}
      </div>
  )
}

