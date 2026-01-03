'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute, Card, Button } from '@edudron/ui-components'
import { enrollmentsApi, coursesApi } from '@/lib/api'
import type { Batch, Course } from '@edudron/shared-utils'

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

export default function BatchesPage() {
  const router = useRouter()
  const [batches, setBatches] = useState<Batch[]>([])
  const [courses, setCourses] = useState<Record<string, Course>>({})
  const [loading, setLoading] = useState(true)

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

  const handleDelete = async (batchId: string) => {
    if (confirm('Are you sure you want to deactivate this batch?')) {
      try {
        await enrollmentsApi.deleteBatch(batchId)
        await loadBatches()
      } catch (error) {
        alert('Failed to delete batch')
      }
    }
  }

  return (
    <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER']}>
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="bg-white shadow-sm border-b border-gray-200">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              <div className="flex items-center space-x-8">
                <h1
                  className="text-2xl font-bold text-blue-600 cursor-pointer"
                  onClick={() => router.push('/dashboard')}
                >
                  EduDron Admin
                </h1>
                <nav className="hidden md:flex space-x-6">
                  <button
                    onClick={() => router.push('/dashboard')}
                    className="text-gray-700 hover:text-blue-600"
                  >
                    Dashboard
                  </button>
                  <button
                    onClick={() => router.push('/courses')}
                    className="text-gray-700 hover:text-blue-600"
                  >
                    Courses
                  </button>
                  <button
                    onClick={() => router.push('/batches')}
                    className="text-gray-700 hover:text-blue-600 font-medium"
                  >
                    Batches
                  </button>
                </nav>
              </div>
            </div>
          </div>
        </header>

        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          {/* Page Header */}
          <div className="mb-6 flex items-center justify-between">
            <div>
              <h2 className="text-3xl font-bold text-gray-900 mb-2">Batch Management</h2>
              <p className="text-gray-600">Manage course batches and enrollments</p>
            </div>
            <Button onClick={() => router.push('/batches/new')}>
              <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Create Batch
            </Button>
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
                          {batch.capacity && (
                            <span>
                              Capacity: {batch.enrolledCount || 0} / {batch.capacity}
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
                          onClick={() => handleDelete(batch.id)}
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
        </main>
      </div>
    </ProtectedRoute>
  )
}

